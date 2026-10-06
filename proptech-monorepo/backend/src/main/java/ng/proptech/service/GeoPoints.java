package ng.proptech.service;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

/** Tiny helper so services never juggle JTS Point/Coordinate construction directly. SRID 4326 = plain WGS84 lat/lng. */
public final class GeoPoints {

    private static final GeometryFactory FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    private GeoPoints() {}

    /** JTS convention: X = longitude, Y = latitude. */
    public static Point of(double latitude, double longitude) {
        return FACTORY.createPoint(new Coordinate(longitude, latitude));
    }

    public static Double lat(Point p) {
        return p == null ? null : p.getY();
    }

    public static Double lng(Point p) {
        return p == null ? null : p.getX();
    }
}
