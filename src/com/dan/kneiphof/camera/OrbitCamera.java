package com.dan.kneiphof.camera;

import com.dan.kneiphof.math.Mat4;
import com.dan.kneiphof.world.Site;

/**
 * Kamera, die um einen Drehpunkt kreist. Maus: links ziehen dreht, rechts ziehen verschiebt, das Rad
 * zoomt (in Schritten von 12 %, vom Geländer bis zum Überblick über die ganze Stadt). Alle Werte
 * laufen weich ihrem Ziel nach, damit Zoom und Drehung nicht ruckeln. Rundflug dreht langsam weiter.
 * <p>
 * Die Kamera bleibt über dem Gelände, über dem Wasser und über den Dächern der Stadt (Town.solidTop).
 */
public final class OrbitCamera {
    /** Zielwerte (was die Eingabe setzt) und aktuelle Werte (was gezeichnet wird). */
    public double tx = -40, ty = 4, tz = 10, yaw = Math.toRadians(215), pitch = Math.toRadians(24), dist = 900;
    private double cx = tx, cy = ty, cz = tz, cyaw = yaw, cpitch = pitch, cdist = dist;
    public double fovy = 50;
    public boolean autoOrbit;
    /** Grad je Sekunde im Rundflug. */
    public double orbitSpeed = 4.0;

    public static final double MIN_DIST = 6, MAX_DIST = 5200;

    public double ex, ey, ez;

    // ---- Rundgang zu Fuß (im Dom)
    public volatile boolean walking;
    public volatile boolean kFwd, kBack, kLeft, kRight, kRun, kTurnL, kTurnR, kUp, kDown;
    private double wx, wz, wyaw, wpitch, weye;
    public static final double EYE = 1.65;

    /** Zu Fuß losgehen an (x, z), Blickrichtung wie yaw (Grad) der Kreiskamera. */
    public void startWalk(double x, double z, double yawDeg, double floorY) {
        wx = x; wz = z; wyaw = Math.toRadians(yawDeg); wpitch = Math.toRadians(2);
        weye = floorY + EYE;
        walking = true;
        autoOrbit = false;
        place();
    }

    /** Rundgang beenden; die Kreiskamera übernimmt an der Stelle, in der Blickrichtung. */
    public void stopWalk() {
        if (!walking) return;
        walking = false;
        double d = 14;
        double cp = Math.cos(wpitch);
        yaw = cyaw = wyaw;
        pitch = cpitch = Math.toRadians(12);
        dist = cdist = d;
        tx = cx = wx - Math.sin(wyaw) * d * 0.9;
        tz = cz = wz - Math.cos(wyaw) * d * 0.9;
        ty = cy = Math.max(0, Site.height(tx, tz)) + 1.5;
        place();
    }

    public void look(double dYaw, double dPitch) {
        wyaw += dYaw;
        wpitch = Math.max(Math.toRadians(-80), Math.min(Math.toRadians(80), wpitch + dPitch));
    }

    private void walkStep(double dt) {
        if (kTurnL) wyaw += 1.4 * dt;
        if (kTurnR) wyaw -= 1.4 * dt;
        if (kUp) wpitch = Math.min(Math.toRadians(80), wpitch + 1.0 * dt);
        if (kDown) wpitch = Math.max(Math.toRadians(-80), wpitch - 1.0 * dt);
        double f = (kFwd ? 1 : 0) - (kBack ? 1 : 0), s = (kRight ? 1 : 0) - (kLeft ? 1 : 0);
        if (f != 0 || s != 0) {
            double sp = (kRun ? 4.2 : 1.7) * dt / Math.max(1.0, Math.hypot(f, s));
            // Blickrichtung (x, z) = −(sin yaw, cos yaw); rechts davon = (cos yaw, −sin yaw)
            double dx = (-Math.sin(wyaw) * f + Math.cos(wyaw) * s) * sp, dz = (-Math.cos(wyaw) * f - Math.sin(wyaw) * s) * sp;
            if (com.dan.kneiphof.world.DomInterior.walkable(wx + dx, wz + dz)) { wx += dx; wz += dz; }
            else if (com.dan.kneiphof.world.DomInterior.walkable(wx + dx, wz)) wx += dx;
            else if (com.dan.kneiphof.world.DomInterior.walkable(wx, wz + dz)) wz += dz;
        }
        double target = com.dan.kneiphof.world.DomInterior.floorAt(wx, wz) + EYE;
        weye += (target - weye) * Math.min(1.0, dt * 8.0);
        if (com.dan.kneiphof.world.DomInterior.outside(wx, wz)) stopWalk();
    }

    /** Von der Eingabe gesetzt, wenn jemand die Kamera selbst bewegt (beendet eine Regiefahrt). */
    public volatile boolean userMoved;

    public void orbit(double dYaw, double dPitch) {
        userMoved = true;
        yaw += dYaw;
        pitch = Math.max(Math.toRadians(-6), Math.min(Math.toRadians(88), pitch + dPitch));
    }

    public void zoom(double steps) {
        userMoved = true;
        dist = Math.max(MIN_DIST, Math.min(MAX_DIST, dist * Math.pow(1.12, steps)));
    }

    /** Verschiebt den Drehpunkt in der Bildebene; dx, dy in Bruchteilen der Bildhöhe. */
    public void pan(double dx, double dy) {
        userMoved = true;
        double s = dist * Math.tan(Math.toRadians(fovy) / 2) * 2;
        double rx = Math.cos(yaw), rz = -Math.sin(yaw);
        // „hoch“ im Bild als Bewegung über den Boden nach vorn
        double fx = -Math.sin(yaw), fz = -Math.cos(yaw);
        tx += -dx * s * rx + dy * s * fx;
        tz += -dx * s * rz + dy * s * fz;
        double lim = Site.EXTENT * 0.8;
        tx = Math.max(-lim, Math.min(lim, tx));
        tz = Math.max(-lim, Math.min(lim, tz));
        ty = Math.max(0, Site.height(tx, tz));
    }

    public void lookAt(double x, double y, double z, double yawDeg, double pitchDeg, double d) {
        tx = x; ty = y; tz = z;
        yaw = Math.toRadians(yawDeg); pitch = Math.toRadians(pitchDeg); dist = d;
    }

    /** Sofort an das Ziel springen (beim Start und für Standbilder). */
    public void snap() { cx = tx; cy = ty; cz = tz; cyaw = yaw; cpitch = pitch; cdist = dist; place(); }

    /** Weiterrechnen um dt Sekunden. */
    public void update(double dt) {
        if (walking) { walkStep(dt); if (walking) { place(); return; } }
        if (autoOrbit) yaw += Math.toRadians(orbitSpeed) * dt;
        flyStep(dt);
        double k = 1 - Math.exp(-dt * 7.5);
        cx += (tx - cx) * k; cy += (ty - cy) * k; cz += (tz - cz) * k;
        cyaw += (yaw - cyaw) * k; cpitch += (pitch - cpitch) * k;
        cdist = Math.exp(Math.log(cdist) + (Math.log(dist) - Math.log(cdist)) * k);
        place();
    }

    /** W/A/S/D außerhalb des Doms: der Drehpunkt gleitet vor, zurück und zur Seite; Umschalt macht schneller. */
    private void flyStep(double dt) {
        double f = (kFwd ? 1 : 0) - (kBack ? 1 : 0), s = (kRight ? 1 : 0) - (kLeft ? 1 : 0);
        if (f == 0 && s == 0) return;
        userMoved = true;
        double sp = Math.max(4.0, dist * 0.55) * (kRun ? 3.0 : 1.0) * dt / Math.max(1.0, Math.hypot(f, s));
        double fx = -Math.sin(yaw), fz = -Math.cos(yaw), rx = Math.cos(yaw), rz = -Math.sin(yaw);
        tx += (fx * f + rx * s) * sp;
        tz += (fz * f + rz * s) * sp;
        double lim = Site.EXTENT * 0.8;
        tx = Math.max(-lim, Math.min(lim, tx));
        tz = Math.max(-lim, Math.min(lim, tz));
        ty = Math.max(0, Site.height(tx, tz));
    }

    private void place() {
        if (walking) {
            double cp = Math.cos(wpitch);
            ex = wx; ey = weye; ez = wz;
            double dx = -Math.sin(wyaw) * cp, dy = Math.sin(wpitch), dz = -Math.cos(wyaw) * cp;
            cx = ex + dx * 5; cy = ey + dy * 5; cz = ez + dz * 5;
            cdist = 5;
            return;
        }
        double cp = Math.cos(cpitch);
        // Blickrichtung zum Drehpunkt: yaw 0 schaut nach Norden (−z)
        ex = cx + Math.sin(cyaw) * cp * cdist;
        ez = cz + Math.cos(cyaw) * cp * cdist;
        ey = cy + Math.sin(cpitch) * cdist;
        double ground = Math.max(0.0, Site.height(ex, ez)) + 1.6;
        if (ey < ground) ey = ground;
        // Kein Flug durch Dächer: liegt das Auge über einem Gebäude, hebt es sich darüber (weich, damit es nicht springt)
        double roof = com.dan.kneiphof.world.Town.solidTop(ex, ez, 1.2);
        if (roof > Double.NEGATIVE_INFINITY) {
            double want = roof + 1.2;
            if (ey < want) {
                ey = want;
                // Blick von oben zum Drehpunkt bleibt erhalten
            }
        }
    }

    public float[] view() { return Mat4.lookAt(ex, ey, ez, cx, cy, cz); }

    public float[] projection(double aspect, boolean reversedZ) {
        double near = walking ? 0.1 : Math.max(0.25, Math.min(4.0, cdist * 0.01));
        return Mat4.perspective(fovy, aspect, near, 20000, reversedZ);
    }

    public double distance() { return cdist; }

    /** Blickrichtung (normiert) vom Auge zum Drehpunkt. */
    public double[] forward() {
        double fx = cx - ex, fy = cy - ey, fz = cz - ez;
        double l = Math.sqrt(fx * fx + fy * fy + fz * fz);
        return new double[]{fx / l, fy / l, fz / l};
    }
    public double yawDeg() { return (Math.toDegrees(cyaw) % 360 + 360) % 360; }
    public double pitchDeg() { return Math.toDegrees(cpitch); }
}
