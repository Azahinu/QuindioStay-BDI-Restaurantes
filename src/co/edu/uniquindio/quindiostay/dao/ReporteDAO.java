package co.edu.uniquindio.quindiostay.dao;

import co.edu.uniquindio.quindiostay.config.ConexionBD;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DAO de Reportes y Vistas SQL (Clase 18: Vista + PreparedStatement + ResultSet).
 * Ejecuta los 10 reportes oficiales (R-01 a R-10), vistas de clase y consultas de sustentacion.
 */
public class ReporteDAO {

    public record ReporteResultado(
            String codigo,
            String titulo,
            String nivel,
            String vistaSql,
            String sqlEjecutado,
            List<String> columnas,
            List<Map<String, Object>> filas
    ) {
        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("ok", true);
            m.put("codigo", codigo);
            m.put("titulo", titulo);
            m.put("nivel", nivel);
            m.put("vistaSql", vistaSql);
            m.put("sql", sqlEjecutado);
            m.put("columnas", columnas);
            m.put("filas", filas);
            return m;
        }
    }

    public List<Map<String, Object>> listarMetadatosReportes() {
        return List.of(
                meta("R-01", "Platos disponibles por rango de precio", "Simple (1 tabla)", "Plato", "vw_Reporte01_PlatosDisponibles", false),
                meta("R-02", "Inventario y capacidad de mesas por zona", "Simple (1 tabla)", "Mesa", "vw_Reporte02_MesasPorZona", false),
                meta("R-03", "Directorio de domiciliarios activos por vehículo", "Simple (1 tabla)", "Domiciliario", "vw_Reporte03_DomiciliariosActivos", false),
                meta("R-04", "Platos más vendidos por categoría e ingresos", "Intermedio (4 tablas + Gráfico)", "DetallePedido, Pedido, Plato, CategoriaPlato", "vw_Reporte04_BaseVentas", true),
                meta("R-05", "Ocupación por franja horaria y día de la semana", "Intermedio (4 tablas + Gráfico)", "Reserva, ReservaMesa, Mesa, Prestador", "vw_Reporte05_OcupacionFranjaDia", true),
                meta("R-06", "Ingresos por restaurante, municipio y modalidad", "Intermedio (3 tablas + Gráfico)", "Pedido, Prestador, Municipio", "vw_Reporte06_IngresosModalidad", true),
                meta("R-07", "Entregas realizadas, cancelados y envíos por repartidor", "Intermedio (3 tablas)", "Pedido, Domiciliario, Prestador", "vw_Reporte07_EntregasDomiciliario", false),
                meta("R-08", "Platos sin rotación o bajo el promedio de su categoría", "Avanzado (Subconsulta correlacionada + NOT EXISTS)", "Plato, CategoriaPlato, DetallePedido, Prestador", "vw_Reporte08_PlatosBajoPromedioCategoria", false),
                meta("R-09", "Clientes frecuentes con consumo > promedio general", "Avanzado (Subconsulta + PerfilCliente)", "Cliente, PerfilCliente, Reserva, Pago, Pedido", "vw_Reporte09_ClientesFrecuentes", false),
                meta("R-10", "Restaurantes con tasa de cancelación % > promedio del Quindío", "Avanzado (Subconsulta + Regla 80%)", "Prestador, Municipio, Plan, Reserva, Pago", "vw_Reporte10_RestaurantesCancelaciones", false)
        );
    }

    private static Map<String, Object> meta(String cod, String tit, String niv, String tab, String vista, boolean graf) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("codigo", cod);
        m.put("titulo", tit);
        m.put("nivel", niv);
        m.put("tablas", tab);
        m.put("vista", vista);
        m.put("grafico", graf);
        return m;
    }

    public ReporteResultado ejecutarReporte(String codigo, Map<String, String> params) throws SQLException {
        String sql;
        List<Object> sqlParams = new ArrayList<>();
        String titulo;
        String nivel;
        String vista;

        switch (codigo) {
            case "R-01" -> {
                double min = parseDouble(params.get("minPrecio"), 10000);
                double max = parseDouble(params.get("maxPrecio"), 60000);
                titulo = "R-01: Catálogo de platos disponibles por rango de precio";
                nivel = "Simple (1 tabla: Plato)";
                vista = "vw_Reporte01_PlatosDisponibles";
                sql = "SELECT id_plato, id_prestador, nombre_plato, descripcion, precio_actual, estado "
                        + "FROM vw_Reporte01_PlatosDisponibles "
                        + "WHERE precio_actual BETWEEN ? AND ? "
                        + "ORDER BY precio_actual DESC, nombre_plato ASC";
                sqlParams.add(min);
                sqlParams.add(max);
            }
            case "R-02" -> {
                String zona = params.getOrDefault("zona", "").trim();
                titulo = "R-02: Inventario y capacidad de mesas por zona física";
                nivel = "Simple (1 tabla: Mesa)";
                vista = "vw_Reporte02_MesasPorZona";
                if (!zona.isEmpty()) {
                    sql = "SELECT id_mesa, id_prestador, numero_mesa, zona, capacidad, estado "
                            + "FROM vw_Reporte02_MesasPorZona WHERE zona = ? "
                            + "ORDER BY zona ASC, capacidad DESC, numero_mesa ASC";
                    sqlParams.add(zona);
                } else {
                    sql = "SELECT id_mesa, id_prestador, numero_mesa, zona, capacidad, estado "
                            + "FROM vw_Reporte02_MesasPorZona "
                            + "ORDER BY zona ASC, capacidad DESC, numero_mesa ASC";
                }
            }
            case "R-03" -> {
                String vehiculo = params.getOrDefault("vehiculo", "").trim();
                titulo = "R-03: Directorio de domiciliarios activos por tipo de vehículo";
                nivel = "Simple (1 tabla: Domiciliario)";
                vista = "vw_Reporte03_DomiciliariosActivos";
                if (!vehiculo.isEmpty()) {
                    sql = "SELECT id_domiciliario, documento, nombres, apellidos, telefono, tipo_vehiculo, estado "
                            + "FROM vw_Reporte03_DomiciliariosActivos WHERE tipo_vehiculo = ? "
                            + "ORDER BY tipo_vehiculo ASC, apellidos ASC";
                    sqlParams.add(vehiculo);
                } else {
                    sql = "SELECT id_domiciliario, documento, nombres, apellidos, telefono, tipo_vehiculo, estado "
                            + "FROM vw_Reporte03_DomiciliariosActivos "
                            + "ORDER BY tipo_vehiculo ASC, apellidos ASC";
                }
            }
            case "R-04" -> {
                String desde = params.getOrDefault("desde", "2025-01-01").trim();
                String hasta = params.getOrDefault("hasta", "2027-12-31").trim();
                int minCant = parseInt(params.get("minCantidad"), 10);
                titulo = "R-04: Platos más vendidos por categoría e ingresos históricos";
                nivel = "Intermedio (4 tablas + Gráfico de barras)";
                vista = "vw_Reporte04_BaseVentas";
                sql = "SELECT nombre_categoria, nombre_plato, "
                        + "COUNT(id_pedido) AS comandas_donde_aparece, "
                        + "SUM(cantidad) AS unidades_vendidas, "
                        + "ROUND(AVG(precio_unitario_historico)) AS precio_promedio_cobrado, "
                        + "SUM(subtotal) AS ingresos_historicos "
                        + "FROM vw_Reporte04_BaseVentas "
                        + "WHERE fecha_hora BETWEEN ? AND ? "
                        + "GROUP BY nombre_categoria, nombre_plato "
                        + "HAVING SUM(cantidad) >= ? "
                        + "ORDER BY ingresos_historicos DESC LIMIT 25";
                sqlParams.add(desde);
                sqlParams.add(hasta);
                sqlParams.add(minCant);
            }
            case "R-05" -> {
                String franja = params.getOrDefault("franja", "").trim();
                titulo = "R-05: Ocupación por franja horaria y día de la semana";
                nivel = "Intermedio (4 tablas + Gráfico)";
                vista = "vw_Reporte05_OcupacionFranjaDia";
                if (!franja.isEmpty()) {
                    sql = "SELECT nombre_comercial, dia_semana, franja_horaria, total_reservas, mesas_acopladas, total_comensales "
                            + "FROM vw_Reporte05_OcupacionFranjaDia WHERE franja_horaria = ? "
                            + "ORDER BY total_comensales DESC, num_dia ASC LIMIT 35";
                    sqlParams.add(franja);
                } else {
                    sql = "SELECT nombre_comercial, dia_semana, franja_horaria, total_reservas, mesas_acopladas, total_comensales "
                            + "FROM vw_Reporte05_OcupacionFranjaDia "
                            + "ORDER BY total_comensales DESC, num_dia ASC LIMIT 35";
                }
            }
            case "R-06" -> {
                titulo = "R-06: Ingresos por restaurante, municipio y modalidad de pedido";
                nivel = "Intermedio (3 tablas + Gráfico circular)";
                vista = "vw_Reporte06_IngresosModalidad";
                sql = "SELECT municipio, nombre_comercial, tipo_pedido, cantidad_pedidos, ticket_promedio, recaudo_total "
                        + "FROM vw_Reporte06_IngresosModalidad "
                        + "ORDER BY recaudo_total DESC LIMIT 36";
            }
            case "R-07" -> {
                titulo = "R-07: Entregas realizadas, cancelados y recaudo de envíos por domiciliario";
                nivel = "Intermedio (3 tablas)";
                vista = "vw_Reporte07_EntregasDomiciliario";
                sql = "SELECT documento, domiciliario, tipo_vehiculo, restaurante_base, "
                        + "entregas_realizadas, domicilios_cancelados, total_envios_cobrados, valor_pedidos_transportados "
                        + "FROM vw_Reporte07_EntregasDomiciliario "
                        + "ORDER BY entregas_realizadas DESC, valor_pedidos_transportados DESC";
            }
            case "R-08" -> {
                titulo = "R-08: Platos sin ventas o por debajo del promedio de su categoría";
                nivel = "Avanzado (Subconsulta correlacionada + NOT EXISTS)";
                vista = "vw_Reporte08_PlatosBajoPromedioCategoria";
                sql = "SELECT nombre_categoria, id_plato, nombre_plato, nombre_comercial, precio_actual, "
                        + "unidades_vendidas, ingresos_acumulados, promedio_ingresos_categoria "
                        + "FROM vw_Reporte08_PlatosBajoPromedioCategoria "
                        + "ORDER BY ingresos_acumulados ASC, id_plato ASC";
            }
            case "R-09" -> {
                titulo = "R-09: Clientes frecuentes con consumo > promedio general";
                nivel = "Avanzado (Subconsulta + PerfilCliente)";
                vista = "vw_Reporte09_ClientesFrecuentes";
                sql = "SELECT id_cliente, documento, cliente, ciudad_origen, canal_preferido, acepta_promociones, "
                        + "reservas_pagadas, gastado_reservas, pedidos_entregados, gastado_pedidos, gasto_total_plataforma "
                        + "FROM vw_Reporte09_ClientesFrecuentes "
                        + "ORDER BY gasto_total_plataforma DESC LIMIT 35";
            }
            case "R-10" -> {
                titulo = "R-10: Restaurantes con tasa de cancelación % > promedio del Quindío";
                nivel = "Avanzado (Subconsulta + Regla 80%)";
                vista = "vw_Reporte10_RestaurantesCancelaciones";
                sql = "SELECT municipio, nombre_comercial, total_reservas, reservas_canceladas, "
                        + "tasa_cancelacion_pct, total_reembolsado_80, total_retenido "
                        + "FROM vw_Reporte10_RestaurantesCancelaciones "
                        + "ORDER BY tasa_cancelacion_pct DESC, total_reembolsado_80 DESC";
            }
            default -> throw new IllegalArgumentException("Código de reporte inválido: " + codigo);
        }

        return ejecutarConsulta(codigo, titulo, nivel, vista, sql, sqlParams);
    }

    public ReporteResultado ejecutarConsultaLibre(String sqlRaw) throws SQLException {
        String clean = sqlRaw == null ? "" : sqlRaw.trim();
        String upper = clean.toUpperCase();
        if (!(upper.startsWith("SELECT") || upper.startsWith("WITH") || upper.startsWith("PRAGMA"))) {
            throw new IllegalArgumentException("Por seguridad, la consola SQL solo permite consultas de lectura (SELECT / WITH).");
        }
        return ejecutarConsulta("SQL-LIBRE", "Consulta SQL en Vivo", "Consola JDBC / ResultSet", "Consulta personalizada", clean, List.of());
    }

    public ReporteResultado ejecutarConsulta(String codigo, String titulo, String nivel, String vista, String sql, List<Object> params) throws SQLException {
        List<String> cols = new ArrayList<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection con = ConexionBD.obtenerConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData md = rs.getMetaData();
                int count = md.getColumnCount();
                for (int i = 1; i <= count; i++) {
                    cols.add(md.getColumnLabel(i));
                }
                while (rs.next()) {
                    Map<String, Object> r = new LinkedHashMap<>();
                    for (int i = 1; i <= count; i++) {
                        r.put(cols.get(i - 1), rs.getObject(i));
                    }
                    rows.add(r);
                    if (rows.size() >= 200) break;
                }
            }
        }
        return new ReporteResultado(codigo, titulo, nivel, vista, sql, cols, rows);
    }

    private static double parseDouble(String v, double def) {
        try {
            return (v == null || v.isBlank()) ? def : Double.parseDouble(v.trim());
        } catch (Exception e) {
            return def;
        }
    }

    private static int parseInt(String v, int def) {
        try {
            return (v == null || v.isBlank()) ? def : Integer.parseInt(v.trim());
        } catch (Exception e) {
            return def;
        }
    }
}
