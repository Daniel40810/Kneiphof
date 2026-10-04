-- ============================================================================
-- Kneiphof, Phase 11: Paket KNH_API
-- Sitzung beginnen und beenden, Protokoll schreiben, Zustaende aufraeumen.
-- Die Prozeduren committen nicht; das Programm arbeitet mit Auto-Commit.
-- ============================================================================

CREATE OR REPLACE PACKAGE KNH_API AS
  /** Kennung des Pakets, fuer die Pruefung durch den Einrichter. */
  FUNCTION version RETURN VARCHAR2;

  /** Legt eine Sitzung an und liefert ihre SESSION_ID. */
  FUNCTION session_start(
    p_rechner         IN VARCHAR2,
    p_benutzer        IN VARCHAR2,
    p_app_version     IN VARCHAR2,
    p_java_version    IN VARCHAR2,
    p_grafik          IN VARCHAR2,
    p_kerne           IN NUMBER,
    p_speicher_max_mb IN NUMBER,
    p_fenster_b       IN NUMBER,
    p_fenster_h       IN NUMBER,
    p_zustand_geladen IN VARCHAR2) RETURN NUMBER;

  /** Schliesst die Sitzung: Ende, Dauer, mittlere Bilder je Sekunde, ms je Bild, Speicher. */
  PROCEDURE session_end(
    p_session_id  IN NUMBER,
    p_bilder_s    IN NUMBER,
    p_ms_bild     IN NUMBER,
    p_speicher_mb IN NUMBER);

  /** Schreibt eine Zeile ins Protokoll. */
  PROCEDURE log_event(
    p_session_id IN NUMBER,
    p_sekunde    IN NUMBER,
    p_art        IN VARCHAR2,
    p_ziel       IN VARCHAR2 DEFAULT NULL,
    p_wert       IN VARCHAR2 DEFAULT NULL,
    p_tag        IN NUMBER   DEFAULT NULL,
    p_stunde     IN NUMBER   DEFAULT NULL);

  /** STATE_ID des juengsten Zustands der Art ('AUTO' oder 'MANUELL'), sonst NULL. */
  FUNCTION state_latest(p_art IN VARCHAR2 DEFAULT 'AUTO') RETURN NUMBER;

  /** Behaelt von der Art nur die letzten p_keep Zustaende. */
  PROCEDURE prune_states(p_art IN VARCHAR2 DEFAULT 'AUTO', p_keep IN NUMBER DEFAULT 10);

  /** Loescht Protokollzeilen, die aelter sind als p_tage Tage. */
  PROCEDURE prune_events(p_tage IN NUMBER DEFAULT 90);
END KNH_API;
/

CREATE OR REPLACE PACKAGE BODY KNH_API AS

  FUNCTION version RETURN VARCHAR2 IS
  BEGIN
    RETURN 'KNH_API 1.0 (Phase 11)';
  END version;

  FUNCTION session_start(
    p_rechner         IN VARCHAR2,
    p_benutzer        IN VARCHAR2,
    p_app_version     IN VARCHAR2,
    p_java_version    IN VARCHAR2,
    p_grafik          IN VARCHAR2,
    p_kerne           IN NUMBER,
    p_speicher_max_mb IN NUMBER,
    p_fenster_b       IN NUMBER,
    p_fenster_h       IN NUMBER,
    p_zustand_geladen IN VARCHAR2) RETURN NUMBER IS
    v_id NUMBER;
  BEGIN
    INSERT INTO KNH_SESSION (RECHNER, BENUTZER, APP_VERSION, JAVA_VERSION, GRAFIK, KERNE, SPEICHER_MAX_MB,
                             FENSTER_B, FENSTER_H, ZUSTAND_GELADEN)
    VALUES (SUBSTR(p_rechner, 1, 80), SUBSTR(p_benutzer, 1, 60), SUBSTR(p_app_version, 1, 20),
            SUBSTR(p_java_version, 1, 40), SUBSTR(p_grafik, 1, 120), p_kerne, p_speicher_max_mb,
            p_fenster_b, p_fenster_h, NVL(p_zustand_geladen, 'N'))
    RETURNING SESSION_ID INTO v_id;
    RETURN v_id;
  END session_start;

  PROCEDURE session_end(
    p_session_id  IN NUMBER,
    p_bilder_s    IN NUMBER,
    p_ms_bild     IN NUMBER,
    p_speicher_mb IN NUMBER) IS
  BEGIN
    UPDATE KNH_SESSION
       SET BEENDET     = LOCALTIMESTAMP,
           DAUER_S     = ROUND((CAST(LOCALTIMESTAMP AS DATE) - CAST(GESTARTET AS DATE)) * 86400),
           BILDER_S    = p_bilder_s,
           MS_BILD     = p_ms_bild,
           SPEICHER_MB = p_speicher_mb
     WHERE SESSION_ID = p_session_id;
  END session_end;

  PROCEDURE log_event(
    p_session_id IN NUMBER,
    p_sekunde    IN NUMBER,
    p_art        IN VARCHAR2,
    p_ziel       IN VARCHAR2 DEFAULT NULL,
    p_wert       IN VARCHAR2 DEFAULT NULL,
    p_tag        IN NUMBER   DEFAULT NULL,
    p_stunde     IN NUMBER   DEFAULT NULL) IS
  BEGIN
    INSERT INTO KNH_EVENT (SESSION_ID, SEKUNDE, ART, ZIEL, WERT, TAG, STUNDE)
    VALUES (p_session_id, NVL(p_sekunde, 0), SUBSTR(p_art, 1, 12), SUBSTR(p_ziel, 1, 80),
            SUBSTR(p_wert, 1, 200), p_tag, p_stunde);
  END log_event;

  FUNCTION state_latest(p_art IN VARCHAR2 DEFAULT 'AUTO') RETURN NUMBER IS
    v_id NUMBER;
  BEGIN
    SELECT MAX(STATE_ID) INTO v_id FROM KNH_STATE WHERE ART = p_art;
    RETURN v_id;
  END state_latest;

  PROCEDURE prune_states(p_art IN VARCHAR2 DEFAULT 'AUTO', p_keep IN NUMBER DEFAULT 10) IS
  BEGIN
    DELETE FROM KNH_STATE
     WHERE ART = p_art
       AND STATE_ID NOT IN (SELECT STATE_ID
                              FROM (SELECT STATE_ID FROM KNH_STATE WHERE ART = p_art ORDER BY STATE_ID DESC)
                             WHERE ROWNUM <= p_keep);
  END prune_states;

  PROCEDURE prune_events(p_tage IN NUMBER DEFAULT 90) IS
  BEGIN
    DELETE FROM KNH_EVENT WHERE ZEIT < LOCALTIMESTAMP - NUMTODSINTERVAL(p_tage, 'DAY');
  END prune_events;

END KNH_API;
/
