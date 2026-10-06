"""
NaijaProptech - Local AI Scanner Microservice (MOCK ML PIPELINE)
================================================================

Run locally:
    python -m venv .venv
    .venv\\Scripts\\activate            (Windows)   |   source .venv/bin/activate  (macOS/Linux)
    pip install -r requirements.txt
    uvicorn main:app --host 127.0.0.1 --port 8000 --reload

Contract with the Spring Boot API
---------------------------------
POST /generate   { "image_url": "http://127.0.0.1:8080/media/abc.jpg" }
  ->  {
        "image_url":  "...",
        "phash":      "f8e4c2a19b3d7650",     # 64-bit perceptual hash, 16 hex chars
        "dimensions": 512,
        "vector":     [0.0132, -0.0441, ...],  # exactly 512 floats, L2-normalised
        "model":      "mock-layout-v1",
        "source":     "image" | "fallback"
      }

What is "mocked" here
---------------------
A production build would run a CNN / CLIP-style encoder on the photo. Here we
approximate the *behaviour* the API depends on, deterministically, with only
Pillow + NumPy:

  * pHash    - the classic DCT perceptual hash (32x32 grayscale -> 2D DCT ->
               top-left 8x8 low frequencies -> compare to median -> 64 bits).
  * Vector   - a 32x16 blurred grayscale "layout map" of the room (512 cells),
               mean-centred and L2-normalised. Two photos of the same room
               (different exposure / slight compression) land very close in
               cosine distance; unrelated rooms land far apart. That is
               exactly what the pgvector `<=>` duplicate check needs.

The response shape will NOT change when the mock is swapped for a real model,
so the Spring Boot side needs no modification.
"""

from __future__ import annotations

import hashlib
import io
import logging
import os
from typing import List
from urllib.parse import urlparse

import httpx
import numpy as np
from fastapi import FastAPI, HTTPException
from PIL import Image, ImageFilter, UnidentifiedImageError
from pydantic import BaseModel, Field

# --------------------------------------------------------------------------- #
# Configuration (all overridable through environment variables)
# --------------------------------------------------------------------------- #
VECTOR_DIM = 512

# SSRF guard: this service downloads whatever URL it is given, so only hosts
# on this allow-list may be fetched. Add your S3/CDN host in production.
ALLOWED_IMAGE_HOSTS = {
    h.strip().lower()
    for h in os.getenv("ALLOWED_IMAGE_HOSTS", "127.0.0.1,localhost").split(",")
    if h.strip()
}
MAX_IMAGE_BYTES = int(os.getenv("MAX_IMAGE_BYTES", str(10 * 1024 * 1024)))
FETCH_TIMEOUT_SECONDS = float(os.getenv("FETCH_TIMEOUT_SECONDS", "8"))

# When the image cannot be downloaded/decoded, local demos still work if we
# derive a deterministic pseudo-vector from the URL. Turn this OFF in
# production so failures are loud instead of silently producing fake data.
FALLBACK_ON_FETCH_ERROR = os.getenv("FALLBACK_ON_FETCH_ERROR", "true").lower() == "true"

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")
log = logging.getLogger("ai-scanner")

app = FastAPI(
    title="NaijaProptech AI Scanner (mock)",
    version="0.1.0",
    description="Generates a perceptual hash and a 512-d room-layout vector for property photos.",
)


# --------------------------------------------------------------------------- #
# Schemas
# --------------------------------------------------------------------------- #
class GenerateRequest(BaseModel):
    image_url: str = Field(..., min_length=8, max_length=2048, examples=["http://127.0.0.1:8080/media/room.jpg"])


class GenerateResponse(BaseModel):
    image_url: str
    phash: str
    dimensions: int
    vector: List[float]
    model: str = "mock-layout-v1"
    source: str  # "image" when computed from real pixels, "fallback" otherwise


# --------------------------------------------------------------------------- #
# Image acquisition
# --------------------------------------------------------------------------- #
def _assert_url_allowed(url: str) -> None:
    parsed = urlparse(url)
    if parsed.scheme not in {"http", "https"}:
        raise HTTPException(status_code=400, detail="image_url must be an http(s) URL")
    host = (parsed.hostname or "").lower()
    if host not in ALLOWED_IMAGE_HOSTS:
        raise HTTPException(
            status_code=400,
            detail=f"Host '{host}' is not in ALLOWED_IMAGE_HOSTS",
        )


def _download_image(url: str) -> Image.Image:
    """Download and decode an image with a hard size cap and no redirects."""
    with httpx.Client(timeout=FETCH_TIMEOUT_SECONDS, follow_redirects=False) as client:
        with client.stream("GET", url) as resp:
            resp.raise_for_status()
            buf = io.BytesIO()
            for chunk in resp.iter_bytes():
                buf.write(chunk)
                if buf.tell() > MAX_IMAGE_BYTES:
                    raise ValueError("image exceeds MAX_IMAGE_BYTES")
    buf.seek(0)
    img = Image.open(buf)
    img.load()  # force decode now so corrupt files fail here, not later
    return img


# --------------------------------------------------------------------------- #
# Feature extraction
# --------------------------------------------------------------------------- #
def _dct_matrix(n: int) -> np.ndarray:
    """Orthonormal DCT-II basis matrix (so DCT2(A) = M @ A @ M.T)."""
    k = np.arange(n)[:, None]
    i = np.arange(n)[None, :]
    m = np.cos(np.pi * (2 * i + 1) * k / (2 * n))
    m[0, :] *= 1.0 / np.sqrt(2.0)
    return m * np.sqrt(2.0 / n)


_DCT_32 = _dct_matrix(32)


def compute_phash(img: Image.Image) -> str:
    """64-bit DCT perceptual hash returned as 16 lowercase hex characters."""
    gray = img.convert("L").resize((32, 32), Image.Resampling.LANCZOS)
    pixels = np.asarray(gray, dtype=np.float64)
    dct = _DCT_32 @ pixels @ _DCT_32.T
    low = dct[:8, :8].flatten()
    median = np.median(low[1:])  # ignore the DC term, it only encodes brightness
    bits = (low > median).astype(np.uint8)
    value = int("".join(str(b) for b in bits), 2)
    return f"{value:016x}"


def _l2_normalise(vec: np.ndarray) -> np.ndarray:
    norm = float(np.linalg.norm(vec))
    if norm < 1e-9:  # perfectly flat image - avoid division by zero
        out = np.zeros_like(vec)
        out[0] = 1.0
        return out
    return vec / norm


def compute_layout_vector(img: Image.Image) -> np.ndarray:
    """
    512-d "room layout" descriptor: a 32x16 luminance map of the blurred photo.
    Mean-centring removes global brightness; L2 normalisation removes contrast,
    so exposure differences between two photos of the same room barely matter.
    """
    gray = img.convert("L").filter(ImageFilter.GaussianBlur(radius=2))
    grid = gray.resize((32, 16), Image.Resampling.BOX)
    cells = np.asarray(grid, dtype=np.float64).flatten()
    assert cells.size == VECTOR_DIM
    return _l2_normalise(cells - cells.mean())


def fallback_features(url: str) -> tuple[str, np.ndarray]:
    """Deterministic stand-in features derived from the URL (demo/offline mode)."""
    digest = hashlib.sha256(url.encode("utf-8")).digest()
    rng = np.random.default_rng(int.from_bytes(digest[:8], "big"))
    return digest.hex()[:16], _l2_normalise(rng.standard_normal(VECTOR_DIM))


# --------------------------------------------------------------------------- #
# Endpoints
# --------------------------------------------------------------------------- #
@app.get("/health")
def health() -> dict:
    return {"status": "ok", "vector_dimensions": VECTOR_DIM, "allowed_hosts": sorted(ALLOWED_IMAGE_HOSTS)}


@app.post("/generate", response_model=GenerateResponse)
def generate(req: GenerateRequest) -> GenerateResponse:
    _assert_url_allowed(req.image_url)

    try:
        img = _download_image(req.image_url)
        phash = compute_phash(img)
        vector = compute_layout_vector(img)
        source = "image"
    except (httpx.HTTPError, UnidentifiedImageError, ValueError, OSError) as exc:
        if not FALLBACK_ON_FETCH_ERROR:
            log.error("Could not process %s: %s", req.image_url, exc)
            raise HTTPException(status_code=422, detail=f"Could not fetch or decode image: {exc}") from exc
        log.warning("Falling back to URL-derived vector for %s (%s)", req.image_url, exc)
        phash, vector = fallback_features(req.image_url)
        source = "fallback"

    return GenerateResponse(
        image_url=req.image_url,
        phash=phash,
        dimensions=VECTOR_DIM,
        vector=[round(float(x), 6) for x in vector],
        source=source,
    )


if __name__ == "__main__":
    import uvicorn

    uvicorn.run("main:app", host="127.0.0.1", port=int(os.getenv("PORT", "8000")), reload=True)
