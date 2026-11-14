package co.edu.uniquindio.quindiostay.dao;

import co.edu.uniquindio.quindiostay.config.ConexionBD;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * DAO generico con PreparedStatement y ResultSet para las 21 tablas del modelo.
 * Valida nombres contra lista blanca, expone metadatos de PK/FK y soporta etiquetas de platos.
 */
public class CrudDAO {

    public static final List<String> TABLAS_ORDEN = List.of(
            "Municipio", "TipoPrestador", "Prestador", "Rol", "UsuarioSistema",
            "Cliente", "PerfilCliente", "Plan", "Reserva", "Pago", "Notificacion",
            "Mesa", "ReservaMesa", "CategoriaPlato", "Plato", "Etiqueta",
            "PlatoEtiqueta", "Experiencia", "Domiciliario", "Pedido", "DetallePedido"
    );

    public record ColMeta(String name, String type, boolean notnull, int pkOrder) {
        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", name);
            m.put("type", type);
            m.put("notnull", notnull ? 1 : 0);
            m.put("pk", pkOrder);
            return m;
        }
    }

    public Map<String, Object> obtenerResumenTablas() throws SQLException {
        List<Map<String, Object>> conteos = new ArrayList<>();
        try (Connection con = ConexionBD.obtenerConexion()) {
            for (String tabla : TABLAS_ORDEN) {
                try (PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) AS total FROM " + tabla);
                     ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("tabla", tabla);
                    row.put("filas", rs.getInt("total"));
                    conteos.add(row);
                }
            }
        }
        return Map.of("ok", true, "conteos", conteos);
    }

    public List<ColMeta> obtenerColumnas(Connection con, String tabla) throws SQLException {
        validarTabla(tabla);
        List<ColMeta> cols = new ArrayList<>();
        if ("sqlite".equals(ConexionBD.getEngine())) {
            try (PreparedStatement ps = con.prepareStatement("PRAGMA table_info(" + tabla + ")");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    cols.add(new ColMeta(
                            rs.getString("name"),
                            rs.getString("type"),
                            rs.getInt("notnull") == 1,
                            rs.getInt("pk")
                    ));
                }
            }
            return cols;
        }
        DatabaseMetaData meta = con.getMetaData();
        Set<String> pks = new LinkedHashSet<>();
        try (ResultSet rsPk = meta.getPrimaryKeys(con.getCatalog(), null, tabla)) {
            while (rsPk.next()) {
                pks.add(rsPk.getString("COLUMN_NAME"));
            }
        }
        int pkIdx = 1;
        try (ResultSet rsCol = meta.getColumns(con.getCatalog(), null, tabla, "%")) {
            while (rsCol.next()) {
                String name = rsCol.getString("COLUMN_NAME");
                String type = rsCol.getString("TYPE_NAME");
                boolean nn = rsCol.getInt("NULLABLE") == DatabaseMetaData.columnNoNulls;
                cols.add(new ColMeta(name, type, nn, pks.contains(name) ? pkIdx++ : 0));
            }
        }
        return cols;
    }

    public List<Map<String, Object>> obtenerForaneas(Connection con, String tabla) throws SQLException {
        validarTabla(tabla);
        List<Map<String, Object>> fks = new ArrayList<>();
        if ("sqlite".equals(ConexionBD.getEngine())) {
            try (PreparedStatement ps = con.prepareStatement("PRAGMA foreign_key_list(" + tabla + ")");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> fk = new LinkedHashMap<>();
                    fk.put("from", rs.getString("from"));
                    fk.put("table", rs.getString("table"));
                    fk.put("to", rs.getString("to"));
                    fk.put("on_delete", rs.getString("on_delete"));
                    fks.add(fk);
                }
            }
        }
        return fks;
    }

    public Map<String, Object> listarTabla(String tabla, String filtro, Integer scopePrestadorId) throws SQLException {
        validarTabla(tabla);
        try (Connection con = ConexionBD.obtenerConexion()) {
            List<ColMeta> cols = obtenerColumnas(con, tabla);
            List<Map<String, Object>> fks = obtenerForaneas(con, tabla);
            boolean hasPrestador = cols.stream().anyMatch(c -> "id_prestador".equals(c.name()));

            StringBuilder sql = new StringBuilder("SELECT * FROM ").append(tabla);
            List<Object> params = new ArrayList<>();
            if (scopePrestadorId != null && hasPrestador) {
                sql.append(" WHERE id_prestador = ?");
                params.add(scopePrestadorId);
            }
            if (!cols.isEmpty()) {
                sql.append(" ORDER BY ").append(cols.get(0).name()).append(" DESC");
            }

            List<Map<String, Object>> rows = new ArrayList<>();
            String qLow = filtro == null ? "" : filtro.trim().toLowerCase();
            try (PreparedStatement ps = con.prepareStatement(sql.toString())) {
                for (int i = 0; i < params.size(); i++) {
                    ps.setObject(i + 1, params.get(i));
                }
                try (ResultSet rs = ps.executeQuery()) {
                    ResultSetMetaData md = rs.getMetaData();
                    int colCount = md.getColumnCount();
                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        boolean match = qLow.isEmpty();
                        for (int i = 1; i <= colCount; i++) {
                            Object val = rs.getObject(i);
                            row.put(md.getColumnLabel(i), val);
                            if (!match && val != null && String.valueOf(val).toLowerCase().contains(qLow)) {
                                match = true;
                            }
                        }
                        if (match) {
                            rows.add(row);
                            if (rows.size() >= 150) break;
                        }
                    }
                }
            }

            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("ok", true);
            resp.put("tabla", tabla);
            resp.put("columnas", cols.stream().map(ColMeta::toMap).toList());
            resp.put("foraneas", fks);
            resp.put("filas", rows);
            return resp;
        }
    }

    public Map<String, Object> insertarFila(String tabla, Map<String, Object> valores) throws SQLException {
        validarTabla(tabla);
        try (Connection con = ConexionBD.obtenerConexion()) {
            List<ColMeta> cols = obtenerColumnas(con, tabla);
            List<String> names = new ArrayList<>();
            List<Object> params = new ArrayList<>();
            for (ColMeta c : cols) {
                if (valores.containsKey(c.name())) {
                    Object raw = valores.get(c.name());
                    if (raw == null || String.valueOf(raw).isBlank()) {
                        names.add(c.name());
                        params.add(null);
                    } else {
                        String val = String.valueOf(raw).trim();
                        if ("contrasena_hash".equals(c.name()) && !val.matches("^[0-9a-fA-F]{64}$")) {
                            val = ConexionBD.sha256Hex(val);
                        }
                        names.add(c.name());
                        params.add(coerceValue(c.type(), val));
                    }
                }
            }
            if (names.isEmpty()) {
                return Map.of("ok", false, "error", "No se recibieron columnas válidas para insertar.");
            }
            String placeholders = String.join(", ", names.stream().map(n -> "?").toList());
            String sql = "INSERT INTO " + tabla + " (" + String.join(", ", names) + ") VALUES (" + placeholders + ")";
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                bindParams(ps, params);
                ps.executeUpdate();
            }
            return Map.of("ok", true, "mensaje", "Registro insertado en " + tabla + " mediante PreparedStatement.");
        }
    }

    public Map<String, Object> actualizarFila(String tabla, Map<String, Object> valores) throws SQLException {
        validarTabla(tabla);
        try (Connection con = ConexionBD.obtenerConexion()) {
            List<ColMeta> cols = obtenerColumnas(con, tabla);
            List<ColMeta> pks = cols.stream().filter(c -> c.pkOrder() > 0).toList();
            if (pks.isEmpty()) {
                return Map.of("ok", false, "error", "La tabla no define clave primaria.");
            }
            List<String> setClauses = new ArrayList<>();
            List<Object> params = new ArrayList<>();
            for (ColMeta c : cols) {
                if (c.pkOrder() == 0 && valores.containsKey(c.name())) {
                    Object raw = valores.get(c.name());
                    setClauses.add(c.name() + " = ?");
                    if (raw == null || String.valueOf(raw).isBlank()) {
                        params.add(null);
                    } else {
                        String val = String.valueOf(raw).trim();
                        if ("contrasena_hash".equals(c.name()) && !val.matches("^[0-9a-fA-F]{64}$")) {
                            val = ConexionBD.sha256Hex(val);
                        }
                        params.add(coerceValue(c.type(), val));
                    }
                }
            }
            if (setClauses.isEmpty()) {
                return Map.of("ok", false, "error", "No hay atributos editables por actualizar.");
            }
            List<String> whereClauses = new ArrayList<>();
            for (ColMeta pk : pks) {
                whereClauses.add(pk.name() + " = ?");
                params.add(coerceValue(pk.type(), String.valueOf(valores.get(pk.name()))));
            }
            String sql = "UPDATE " + tabla + " SET " + String.join(", ", setClauses)
                    + " WHERE " + String.join(" AND ", whereClauses);
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                bindParams(ps, params);
                int updated = ps.executeUpdate();
                return Map.of("ok", true, "mensaje", "Registro actualizado (" + updated + " fila/s) en " + tabla + ".");
            }
        }
    }

    public Map<String, Object> eliminarFila(String tabla, Map<String, Object> keys) throws SQLException {
        validarTabla(tabla);
        try (Connection con = ConexionBD.obtenerConexion()) {
            List<ColMeta> cols = obtenerColumnas(con, tabla);
            List<ColMeta> pks = cols.stream().filter(c -> c.pkOrder() > 0).toList();
            List<String> whereClauses = new ArrayList<>();
            List<Object> params = new ArrayList<>();
            for (ColMeta pk : pks) {
                Object raw = keys.get(pk.name());
                if (raw == null || String.valueOf(raw).isBlank()) {
                    return Map.of("ok", false, "error", "Falta la clave primaria: " + pk.name());
                }
                whereClauses.add(pk.name() + " = ?");
                params.add(coerceValue(pk.type(), String.valueOf(raw)));
            }
            String sql = "DELETE FROM " + tabla + " WHERE " + String.join(" AND ", whereClauses);
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                bindParams(ps, params);
                int deleted = ps.executeUpdate();
                return Map.of("ok", true, "mensaje", "Registro eliminado (" + deleted + " fila/s) en " + tabla + ".");
            }
        }
    }

    public Map<String, Object> obtenerEtiquetasPlato(int idPlato) throws SQLException {
        List<Map<String, Object>> todas = new ArrayList<>();
        List<Integer> asignadas = new ArrayList<>();
        try (Connection con = ConexionBD.obtenerConexion()) {
            try (PreparedStatement ps = con.prepareStatement("SELECT id_etiqueta, nombre_etiqueta FROM Etiqueta ORDER BY id_etiqueta");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    todas.add(Map.of("id_etiqueta", rs.getInt("id_etiqueta"), "nombre_etiqueta", rs.getString("nombre_etiqueta")));
                }
            }
            if (idPlato > 0) {
                try (PreparedStatement ps = con.prepareStatement("SELECT id_etiqueta FROM PlatoEtiqueta WHERE id_plato = ?")) {
                    ps.setInt(1, idPlato);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            asignadas.add(rs.getInt("id_etiqueta"));
                        }
                    }
                }
            }
        }
        return Map.of("ok", true, "todas", todas, "asignadas", asignadas);
    }

    public Map<String, Object> guardarEtiquetasPlato(int idPlato, List<?> idsEtiqueta) throws SQLException {
        try (Connection con = ConexionBD.obtenerConexion()) {
            con.setAutoCommit(false);
            try {
                try (PreparedStatement psDel = con.prepareStatement("DELETE FROM PlatoEtiqueta WHERE id_plato = ?")) {
                    psDel.setInt(1, idPlato);
                    psDel.executeUpdate();
                }
                if (idsEtiqueta != null) {
                    try (PreparedStatement psIns = con.prepareStatement(
                            "INSERT INTO PlatoEtiqueta (id_plato, id_etiqueta) VALUES (?, ?)")) {
                        for (Object o : idsEtiqueta) {
                            psIns.setInt(1, idPlato);
                            psIns.setInt(2, Integer.parseInt(String.valueOf(o)));
                            psIns.executeUpdate();
                        }
                    }
                }
                con.commit();
                return Map.of("ok", true, "mensaje", "Etiquetas dietarias actualizadas para el plato #" + idPlato + ".");
            } catch (SQLException ex) {
                con.rollback();
                throw ex;
            } finally {
                con.setAutoCommit(true);
            }
        }
    }

    private static void validarTabla(String tabla) {
        if (!TABLAS_ORDEN.contains(tabla)) {
            throw new IllegalArgumentException("Tabla no autorizada en el modelo: " + tabla);
        }
    }

    private static Object coerceValue(String sqlType, String raw) {
        String upper = sqlType == null ? "" : sqlType.toUpperCase();
        if (upper.contains("INT") || upper.contains("BIT")) {
            return Integer.parseInt(raw);
        }
        if (upper.contains("DEC") || upper.contains("NUM") || upper.contains("FLOAT") || upper.contains("DOUBLE")) {
            return Double.parseDouble(raw);
        }
        return raw;
    }

    private static void bindParams(PreparedStatement ps, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            Object v = params.get(i);
            if (v == null) {
                ps.setNull(i + 1, Types.VARCHAR);
            } else {
                ps.setObject(i + 1, v);
            }
        }
    }
}
