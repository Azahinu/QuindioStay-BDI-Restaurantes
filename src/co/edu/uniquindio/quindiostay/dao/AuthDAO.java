package co.edu.uniquindio.quindiostay.dao;

import co.edu.uniquindio.quindiostay.config.ConexionBD;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * DAO de Autenticacion, Registro y Preferencias de PerfilCliente.
 */
public class AuthDAO {

    public Map<String, Object> autenticar(String tipoCuenta, String identificador, String contrasena) throws SQLException {
        String idLimpio = identificador == null ? "" : identificador.trim();
        String hashIngresado = ConexionBD.sha256Hex(contrasena);

        try (Connection con = ConexionBD.obtenerConexion()) {
            if ("cliente".equalsIgnoreCase(tipoCuenta)) {
                String sql = """
                    SELECT c.id_cliente, c.nombres, c.apellidos, c.correo, c.telefono, c.ciudad_origen,
                           pc.contrasena_hash, pc.idioma, pc.canal_preferido,
                           pc.anticipacion_recordatorio, pc.acepta_promociones
                    FROM Cliente c
                    INNER JOIN PerfilCliente pc ON c.id_cliente = pc.id_cliente
                    WHERE LOWER(c.correo) = LOWER(?)
                    """;
                try (PreparedStatement ps = con.prepareStatement(sql)) {
                    ps.setString(1, idLimpio);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            return error("No existe una cuenta web de cliente registrada con ese correo.");
                        }
                        String hashBD = rs.getString("contrasena_hash");
                        if (!hashIngresado.equalsIgnoreCase(hashBD)) {
                            return error("Contraseña incorrecta para la cuenta de cliente.");
                        }
                        Map<String, Object> resp = new LinkedHashMap<>();
                        resp.put("ok", true);
                        resp.put("usuario", rs.getString("correo"));
                        resp.put("nombre", rs.getString("nombres") + " " + rs.getString("apellidos"));
                        resp.put("rol", "cliente");
                        resp.put("idCliente", rs.getInt("id_cliente"));
                        resp.put("idPrestador", null);
                        resp.put("restaurante", "Cuenta de Turista / Cliente (ID #" + rs.getInt("id_cliente") + ")");
                        resp.put("idioma", rs.getString("idioma"));
                        resp.put("canalPreferido", rs.getString("canal_preferido"));
                        resp.put("anticipacionRecordatorio", rs.getInt("anticipacion_recordatorio"));
                        resp.put("aceptaPromociones", rs.getInt("acepta_promociones") == 1);
                        return resp;
                    }
                }
            } else {
                String sql = """
                    SELECT u.id_usuario, u.nombre_usuario, u.contrasena_hash, u.estado,
                           u.id_prestador, r.nombre_rol, pr.nombre_comercial
                    FROM UsuarioSistema u
                    INNER JOIN Rol r ON u.id_rol = r.id_rol
                    LEFT JOIN Prestador pr ON u.id_prestador = pr.id_prestador
                    WHERE LOWER(u.nombre_usuario) = LOWER(?)
                    """;
                try (PreparedStatement ps = con.prepareStatement(sql)) {
                    ps.setString(1, idLimpio);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            return error("Usuario no encontrado en UsuarioSistema.");
                        }
                        if (!"activo".equalsIgnoreCase(rs.getString("estado"))) {
                            return error("La cuenta se encuentra inactiva o bloqueada.");
                        }
                        String hashBD = rs.getString("contrasena_hash");
                        if (!hashIngresado.equalsIgnoreCase(hashBD)) {
                            return error("Contraseña incorrecta para el usuario interno.");
                        }
                        Object idPrest = rs.getObject("id_prestador");
                        String rest = rs.getString("nombre_comercial");
                        Map<String, Object> resp = new LinkedHashMap<>();
                        resp.put("ok", true);
                        resp.put("idUsuario", rs.getInt("id_usuario"));
                        resp.put("usuario", rs.getString("nombre_usuario"));
                        resp.put("nombre", rs.getString("nombre_usuario"));
                        resp.put("rol", rs.getString("nombre_rol"));
                        resp.put("idPrestador", idPrest == null ? null : ((Number) idPrest).intValue());
                        resp.put("restaurante", rest != null ? rest : "Administración General QuindíoStay");
                        resp.put("idCliente", null);
                        return resp;
                    }
                }
            }
        }
    }

    public Map<String, Object> registrarCliente(Map<String, Object> datos) throws SQLException {
        String clave = str(datos.get("contrasena"));
        if (clave.length() < 6) {
            return error("La contraseña debe tener al menos 6 caracteres.");
        }
        String hash = ConexionBD.sha256Hex(clave);

        try (Connection con = ConexionBD.obtenerConexion()) {
            con.setAutoCommit(false);
            try {
                int nextId;
                try (Statement st = con.createStatement();
                     ResultSet rs = st.executeQuery("SELECT COALESCE(MAX(id_cliente), 0) + 1 FROM Cliente")) {
                    rs.next();
                    nextId = rs.getInt(1);
                }

                String sqlCli = """
                    INSERT INTO Cliente (id_cliente, tipo_documento, numero_documento, nombres, apellidos,
                                         correo, telefono, fecha_nacimiento, ciudad_origen)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """;
                try (PreparedStatement ps = con.prepareStatement(sqlCli)) {
                    ps.setInt(1, nextId);
                    ps.setString(2, strOr(datos.get("tipo_documento"), "CC"));
                    ps.setString(3, str(datos.get("numero_documento")));
                    ps.setString(4, str(datos.get("nombres")));
                    ps.setString(5, str(datos.get("apellidos")));
                    ps.setString(6, str(datos.get("correo")));
                    ps.setString(7, str(datos.get("telefono")));
                    ps.setString(8, strOr(datos.get("fecha_nacimiento"), "1998-05-15"));
                    ps.setString(9, strOr(datos.get("ciudad_origen"), "Armenia"));
                    ps.executeUpdate();
                }

                String sqlPerf = """
                    INSERT INTO PerfilCliente (id_cliente, contrasena_hash, idioma, canal_preferido,
                                               anticipacion_recordatorio, acepta_promociones)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """;
                try (PreparedStatement ps = con.prepareStatement(sqlPerf)) {
                    ps.setInt(1, nextId);
                    ps.setString(2, hash);
                    ps.setString(3, strOr(datos.get("idioma"), "es"));
                    ps.setString(4, strOr(datos.get("canal_preferido"), "correo"));
                    ps.setInt(5, intOr(datos.get("anticipacion_recordatorio"), 24));
                    ps.setInt(6, boolInt(datos.get("acepta_promociones"), 1));
                    ps.executeUpdate();
                }

                con.commit();
                Map<String, Object> ok = new LinkedHashMap<>();
                ok.put("ok", true);
                ok.put("id_cliente", nextId);
                ok.put("mensaje", "Cuenta de cliente y PerfilCliente creados correctamente (ID #" + nextId + ").");
                return ok;
            } catch (SQLException ex) {
                con.rollback();
                throw ex;
            } finally {
                con.setAutoCommit(true);
            }
        }
    }

    public Map<String, Object> obtenerPerfilCliente(int idCliente) throws SQLException {
        String sql = """
            SELECT c.id_cliente, c.tipo_documento, c.numero_documento, c.nombres, c.apellidos,
                   c.correo, c.telefono, c.fecha_nacimiento, c.ciudad_origen,
                   pc.idioma, pc.canal_preferido, pc.anticipacion_recordatorio, pc.acepta_promociones
            FROM Cliente c
            INNER JOIN PerfilCliente pc ON c.id_cliente = pc.id_cliente
            WHERE c.id_cliente = ?
            """;
        try (Connection con = ConexionBD.obtenerConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, idCliente);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return error("Perfil de cliente no encontrado.");
                }
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("ok", true);
                m.put("id_cliente", rs.getInt("id_cliente"));
                m.put("tipo_documento", rs.getString("tipo_documento"));
                m.put("numero_documento", rs.getString("numero_documento"));
                m.put("nombres", rs.getString("nombres"));
                m.put("apellidos", rs.getString("apellidos"));
                m.put("correo", rs.getString("correo"));
                m.put("telefono", rs.getString("telefono"));
                m.put("fecha_nacimiento", rs.getString("fecha_nacimiento"));
                m.put("ciudad_origen", rs.getString("ciudad_origen"));
                m.put("idioma", rs.getString("idioma"));
                m.put("canal_preferido", rs.getString("canal_preferido"));
                m.put("anticipacion_recordatorio", rs.getInt("anticipacion_recordatorio"));
                m.put("acepta_promociones", rs.getInt("acepta_promociones"));
                return m;
            }
        }
    }

    public Map<String, Object> actualizarPerfilCliente(int idCliente, Map<String, Object> datos) throws SQLException {
        try (Connection con = ConexionBD.obtenerConexion()) {
            con.setAutoCommit(false);
            try {
                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE Cliente SET telefono = ?, ciudad_origen = ? WHERE id_cliente = ?")) {
                    ps.setString(1, str(datos.get("telefono")));
                    ps.setString(2, str(datos.get("ciudad_origen")));
                    ps.setInt(3, idCliente);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE PerfilCliente SET idioma = ?, canal_preferido = ?, anticipacion_recordatorio = ?, acepta_promociones = ? WHERE id_cliente = ?")) {
                    ps.setString(1, strOr(datos.get("idioma"), "es"));
                    ps.setString(2, strOr(datos.get("canal_preferido"), "correo"));
                    ps.setInt(3, intOr(datos.get("anticipacion_recordatorio"), 24));
                    ps.setInt(4, boolInt(datos.get("acepta_promociones"), 1));
                    ps.setInt(5, idCliente);
                    ps.executeUpdate();
                }
                con.commit();
                return Map.of("ok", true, "mensaje", "Preferencias del perfil actualizadas correctamente.");
            } catch (SQLException ex) {
                con.rollback();
                throw ex;
            } finally {
                con.setAutoCommit(true);
            }
        }
    }

    private static Map<String, Object> error(String msg) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", false);
        m.put("error", msg);
        return m;
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }

    private static String strOr(Object o, String def) {
        String s = str(o);
        return s.isEmpty() ? def : s;
    }

    private static int intOr(Object o, int def) {
        if (o instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(str(o));
        } catch (Exception e) {
            return def;
        }
    }

    private static int boolInt(Object o, int def) {
        if (o instanceof Boolean b) return b ? 1 : 0;
        if (o instanceof Number n) return n.intValue() != 0 ? 1 : 0;
        String s = str(o).toLowerCase();
        if ("1".equals(s) || "true".equals(s) || "si".equals(s) || "sí".equals(s)) return 1;
        if ("0".equals(s) || "false".equals(s) || "no".equals(s)) return 0;
        return def;
    }
}
