package co.edu.uniquindio.quindiostay.config;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

/**
 * Capa de Conexion JDBC centralizada (Patron DAO - Entrega 2 / Clase 18).
 * Entrega objetos Connection configurados con PreparedStatement y ResultSet
 * hacia SQLite local, MySQL 8.0 (Entrega 2) o SQL Server (Clase 18).
 */
public class ConexionBD {

    private static final Properties PROPS = new Properties();

    static {
        Path cfg = Path.of("config.properties");
        if (Files.exists(cfg)) {
            try (FileInputStream fis = new FileInputStream(cfg.toFile())) {
                PROPS.load(fis);
            } catch (IOException ignored) {
            }
        }
    }

    public static String getEngine() {
        return PROPS.getProperty("db.engine", "sqlite").trim().toLowerCase();
    }

    public static int getServerPort() {
        try {
            return Integer.parseInt(PROPS.getProperty("server.port", "8080").trim());
        } catch (NumberFormatException e) {
            return 8080;
        }
    }

    public static Connection obtenerConexion() throws SQLException {
        String engine = getEngine();
        Connection con;
        switch (engine) {
            case "mysql" -> {
                String url = PROPS.getProperty("db.mysql.url",
                        "jdbc:mysql://localhost:3306/quindiostay?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=America/Bogota");
                String usr = PROPS.getProperty("db.mysql.user", "root");
                String pwd = PROPS.getProperty("db.mysql.password", "");
                con = DriverManager.getConnection(url, usr, pwd);
            }
            case "sqlserver" -> {
                String url = PROPS.getProperty("db.sqlserver.url",
                        "jdbc:sqlserver://localhost:1433;databaseName=QuindioStay;encrypt=false;trustServerCertificate=true");
                String usr = PROPS.getProperty("db.sqlserver.user", "sa");
                String pwd = PROPS.getProperty("db.sqlserver.password", "");
                con = DriverManager.getConnection(url, usr, pwd);
            }
            default -> {
                String dbPath = PROPS.getProperty("db.sqlite.path", "data/quindiostay.db");
                con = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
                try (Statement st = con.createStatement()) {
                    // Garantiza validacion real de claves foraneas RESTRICT, CASCADE y SET NULL
                    st.execute("PRAGMA foreign_keys = ON;");
                    st.execute("PRAGMA busy_timeout = 5000;");
                }
            }
        }
        return con;
    }

    public static void inicializarVistasSiEsSQLite() throws SQLException, IOException {
        if (!"sqlite".equals(getEngine())) {
            return;
        }
        Path script = Path.of("sql", "sqlite", "00_vistas_sqlite.sql");
        if (!Files.exists(script)) {
            script = Path.of("sql", "00_vistas_sqlite.sql");
        }
        if (!Files.exists(script)) {
            return;
        }
        String content = Files.readString(script, StandardCharsets.UTF_8);
        try (Connection con = obtenerConexion(); Statement st = con.createStatement()) {
            for (String stmt : content.split(";(\\r?\\n|$)")) {
                String clean = stmt.trim();
                if (!clean.isEmpty()) {
                    st.execute(clean);
                }
            }
        }
    }

    public static String sha256Hex(String texto) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest((texto == null ? "" : texto).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    public static String traducirErrorSQL(SQLException e) {
        String msg = e.getMessage() == null ? "" : e.getMessage();
        String lower = msg.toLowerCase();
        if (lower.contains("foreign key constraint failed") || lower.contains("a foreign key constraint fails")
                || lower.contains("reference constraint")) {
            return "Regla de integridad referencial (FK / ON DELETE RESTRICT): la operación fue rechazada porque existen registros hijos vinculados o la clave padre no existe.";
        }
        if (lower.contains("unique constraint failed") || lower.contains("duplicate entry")
                || lower.contains("violation of unique key")) {
            return "Clave primaria o alterna duplicada (UNIQUE): ya existe un registro con ese identificador, documento, NIT o correo.";
        }
        if (lower.contains("check constraint failed") || lower.contains("check constraint")) {
            return "Restricción CHECK violada: revise rangos permitidos (capacidad 1..12, precios > 0, modalidad de pedido o fechas válidas).";
        }
        if (lower.contains("not null constraint failed") || lower.contains("cannot be null")) {
            return "Campo obligatorio faltante (NOT NULL): complete todos los campos requeridos por el diccionario de datos.";
        }
        return "Error SQL [" + e.getErrorCode() + "]: " + msg;
    }
}
