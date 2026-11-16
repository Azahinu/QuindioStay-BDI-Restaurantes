package co.edu.uniquindio.quindiostay.dao;

import co.edu.uniquindio.quindiostay.config.ConexionBD;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DAO de Reglas de Negocio Transaccionales (Entrega 2 / Seccion 5.1 Proyecto Final).
 */
public class NegocioDAO {

    public Map<String, Object> obtenerCatalogoOperativo(Integer idPrestadorScope) throws SQLException {
        try (Connection con = ConexionBD.obtenerConexion()) {
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("ok", true);
            resp.put("prestadores", queryList(con,
                    idPrestadorScope == null
                            ? "SELECT p.id_prestador, p.nombre_comercial, m.nombre AS municipio, p.estado FROM Prestador p JOIN Municipio m ON p.codigo_dane=m.codigo_dane WHERE p.estado='activo' ORDER BY p.id_prestador"
                            : "SELECT p.id_prestador, p.nombre_comercial, m.nombre AS municipio, p.estado FROM Prestador p JOIN Municipio m ON p.codigo_dane=m.codigo_dane WHERE p.id_prestador=" + idPrestadorScope));
            resp.put("planes", queryList(con,
                    "SELECT id_plan, id_prestador, nombre, tipo, precio, fecha_inicio_vigencia, fecha_fin_vigencia FROM Plan WHERE estado='activo' ORDER BY id_prestador, id_plan"));
            resp.put("experiencias", queryList(con,
                    "SELECT id_experiencia, id_prestador, nombre_experiencia, cupo_maximo, precio_persona, duracion_horas FROM Experiencia WHERE estado='activa' ORDER BY id_prestador, id_experiencia"));
            resp.put("mesas", queryList(con,
                    "SELECT id_mesa, id_prestador, numero_mesa, capacidad, zona, estado FROM Mesa WHERE estado <> 'fuera_de_servicio' ORDER BY id_prestador, numero_mesa"));
            resp.put("platos", queryList(con,
                    "SELECT id_plato, id_prestador, id_categoria, nombre_plato, precio_actual, estado FROM Plato WHERE estado='disponible' ORDER BY id_prestador, id_plato"));
            String nomDom = concatEspacio("nombres", "apellidos");
            String docCli = concatEspacio("tipo_documento", "numero_documento");
            String nomCli = concatEspacio("c.nombres", "c.apellidos");
            resp.put("domiciliarios", queryList(con,
                    "SELECT id_domiciliario, id_prestador, " + nomDom + " AS nombre, tipo_vehiculo, estado FROM Domiciliario WHERE estado IN ('disponible','en_ruta') ORDER BY id_prestador, id_domiciliario"));
            resp.put("clientes", queryList(con,
                    "SELECT id_cliente, " + docCli + " AS doc, " + concatEspacio("nombres", "apellidos") + " AS nombre, correo FROM Cliente ORDER BY id_cliente DESC LIMIT 80"));
            resp.put("reservasRecientes", queryList(con,
                    "SELECT r.id_reserva, " + nomCli + " AS cliente, pr.nombre_comercial AS prestador, r.fecha_servicio, r.franja_horaria, r.numero_comensales, r.valor_total, r.estado FROM Reserva r JOIN Cliente c ON r.id_cliente=c.id_cliente LEFT JOIN Plan pl ON r.id_plan=pl.id_plan LEFT JOIN Experiencia ex ON r.id_experiencia=ex.id_experiencia LEFT JOIN Prestador pr ON pr.id_prestador=COALESCE(pl.id_prestador, ex.id_prestador) ORDER BY r.id_reserva DESC LIMIT 40"));
            resp.put("pedidosRecientes", queryList(con,
                    "SELECT pe.id_pedido, pr.nombre_comercial AS prestador, pe.fecha_hora, pe.tipo_pedido, pe.total_pedido, pe.estado_pedido FROM Pedido pe JOIN Prestador pr ON pe.id_prestador=pr.id_prestador ORDER BY pe.id_pedido DESC LIMIT 30"));
            return resp;
        }
    }

    public Map<String, Object> crearReservaCompleta(Map<String, Object> datos) throws SQLException {
        int idCliente = toInt(datos.get("id_cliente"), 0);
        Integer idPlan = toNullableInt(datos.get("id_plan"));
        Integer idExperiencia = toNullableInt(datos.get("id_experiencia"));
        String fechaServicio = str(datos.get("fecha_servicio"));
        String franja = strOr(datos.get("franja_horaria"), "almuerzo");
        int comensales = toInt(datos.get("numero_comensales"), 2);
        String canal = strOr(datos.get("canal"), "web");
        String metodoPago = strOr(datos.get("metodo_pago"), "PSE");
        double montoPago = toDouble(datos.get("monto_pago"), 0);
        String refTx = str(datos.get("referencia_transaccion"));
        List<?> mesasRaw = (datos.get("id_mesas") instanceof List<?> l) ? l : List.of();

        if (idCliente <= 0 || fechaServicio.isEmpty()) {
            return err("Debe seleccionar un cliente y una fecha de servicio válida.");
        }
        if (idPlan == null && idExperiencia == null) {
            return err("Debe seleccionar un Plan turístico o una Experiencia gastronómica.");
        }
        if (mesasRaw.isEmpty()) {
            return err("Debe seleccionar al menos una mesa física para asociar en ReservaMesa.");
        }

        try (Connection con = ConexionBD.obtenerConexion()) {
            con.setAutoCommit(false);
            try {
                int idPrestadorServicio = -1;
                double valorTotal = 0;

                if (idPlan != null) {
                    try (PreparedStatement ps = con.prepareStatement(
                            "SELECT id_prestador, precio, fecha_inicio_vigencia, fecha_fin_vigencia, estado FROM Plan WHERE id_plan = ?")) {
                        ps.setInt(1, idPlan);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (!rs.next()) return rollbackErr(con, "El plan #" + idPlan + " no existe.");
                            if (!"activo".equalsIgnoreCase(rs.getString("estado"))) {
                                return rollbackErr(con, "Regla de negocio: solo se pueden reservar planes activos.");
                            }
                            String ini = rs.getString("fecha_inicio_vigencia");
                            String fin = rs.getString("fecha_fin_vigencia");
                            if (fechaServicio.compareTo(ini) < 0 || fechaServicio.compareTo(fin) > 0) {
                                return rollbackErr(con, "Regla de negocio: la fecha de servicio (" + fechaServicio + ") está fuera de la vigencia del plan (" + ini + " a " + fin + ").");
                            }
                            idPrestadorServicio = rs.getInt("id_prestador");
                            valorTotal += rs.getDouble("precio") * comensales;
                        }
                    }
                }

                if (idExperiencia != null) {
                    try (PreparedStatement ps = con.prepareStatement(
                            "SELECT id_prestador, cupo_maximo, precio_persona, estado FROM Experiencia WHERE id_experiencia = ?")) {
                        ps.setInt(1, idExperiencia);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (!rs.next()) return rollbackErr(con, "La experiencia #" + idExperiencia + " no existe.");
                            if (!"activa".equalsIgnoreCase(rs.getString("estado"))) {
                                return rollbackErr(con, "Solo se pueden reservar experiencias activas.");
                            }
                            if (comensales > rs.getInt("cupo_maximo")) {
                                return rollbackErr(con, "Los comensales (" + comensales + ") superan el cupo máximo de la experiencia (" + rs.getInt("cupo_maximo") + ").");
                            }
                            if (idPrestadorServicio == -1) {
                                idPrestadorServicio = rs.getInt("id_prestador");
                            } else if (idPrestadorServicio != rs.getInt("id_prestador")) {
                                return rollbackErr(con, "El plan y la experiencia deben pertenecer al mismo restaurante.");
                            }
                            valorTotal += rs.getDouble("precio_persona") * comensales;
                        }
                    }
                }

                // Verificar mesas: mismo restaurante, capacidad acumulada >= comensales y no cruzadas
                int sumaCapacidad = 0;
                List<Integer> idMesas = new ArrayList<>();
                for (Object mObj : mesasRaw) {
                    int idMesa = toInt(mObj, 0);
                    if (idMesa <= 0) continue;
                    idMesas.add(idMesa);
                    try (PreparedStatement ps = con.prepareStatement(
                            "SELECT id_prestador, numero_mesa, capacidad, estado FROM Mesa WHERE id_mesa = ?")) {
                        ps.setInt(1, idMesa);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (!rs.next()) return rollbackErr(con, "La mesa #" + idMesa + " no existe.");
                            if (rs.getInt("id_prestador") != idPrestadorServicio) {
                                return rollbackErr(con, "Regla de negocio: la mesa #" + rs.getInt("numero_mesa") + " pertenece a otro restaurante.");
                            }
                            if ("fuera_de_servicio".equalsIgnoreCase(rs.getString("estado"))) {
                                return rollbackErr(con, "La mesa #" + rs.getInt("numero_mesa") + " está fuera de servicio.");
                            }
                            sumaCapacidad += rs.getInt("capacidad");
                        }
                    }
                    try (PreparedStatement psChk = con.prepareStatement("""
                            SELECT r.id_reserva FROM Reserva r
                            JOIN ReservaMesa rm ON r.id_reserva = rm.id_reserva
                            WHERE rm.id_mesa = ? AND r.fecha_servicio = ? AND r.franja_horaria = ? AND r.estado <> 'cancelada'
                            """)) {
                        psChk.setInt(1, idMesa);
                        psChk.setString(2, fechaServicio);
                        psChk.setString(3, franja);
                        try (ResultSet rsChk = psChk.executeQuery()) {
                            if (rsChk.next()) {
                                return rollbackErr(con, "Conflicto de agenda: la mesa #" + idMesa + " ya está reservada en " + fechaServicio + " (" + franja + ") por la reserva #" + rsChk.getInt(1) + ".");
                            }
                        }
                    }
                }

                if (sumaCapacidad < comensales) {
                    return rollbackErr(con, "Capacidad insuficiente: las mesas seleccionadas suman " + sumaCapacidad + " sillas y se requieren " + comensales + " comensales.");
                }

                int idReserva = nextId(con, "Reserva", "id_reserva");
                String hoy = LocalDate.now().toString();
                String estadoInicial = montoPago >= valorTotal ? "confirmada" : "pendiente";

                try (PreparedStatement ps = con.prepareStatement("""
                        INSERT INTO Reserva (id_reserva, id_cliente, id_plan, id_experiencia, fecha_creacion,
                                             fecha_servicio, franja_horaria, numero_comensales, valor_total, canal, estado)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """)) {
                    ps.setInt(1, idReserva);
                    ps.setInt(2, idCliente);
                    if (idPlan != null) ps.setInt(3, idPlan); else ps.setNull(3, Types.INTEGER);
                    if (idExperiencia != null) ps.setInt(4, idExperiencia); else ps.setNull(4, Types.INTEGER);
                    ps.setString(5, hoy);
                    ps.setString(6, fechaServicio);
                    ps.setString(7, franja);
                    ps.setInt(8, comensales);
                    ps.setDouble(9, valorTotal);
                    ps.setString(10, canal);
                    ps.setString(11, estadoInicial);
                    ps.executeUpdate();
                }

                try (PreparedStatement psRm = con.prepareStatement("INSERT INTO ReservaMesa (id_reserva, id_mesa) VALUES (?, ?)")) {
                    for (int mId : idMesas) {
                        psRm.setInt(1, idReserva);
                        psRm.setInt(2, mId);
                        psRm.executeUpdate();
                    }
                }

                Integer idPagoCreado = null;
                if (montoPago > 0) {
                    idPagoCreado = nextId(con, "Pago", "id_pago");
                    String refFinal = refTx.isEmpty() && !"efectivo".equalsIgnoreCase(metodoPago)
                            ? "TX-" + idReserva + "-" + System.currentTimeMillis() % 100000
                            : (refTx.isEmpty() ? null : refTx);
                    try (PreparedStatement psPg = con.prepareStatement("""
                            INSERT INTO Pago (id_pago, id_reserva, fecha_pago, monto, metodo, estado, referencia_transaccion)
                            VALUES (?, ?, ?, ?, ?, 'aprobado', ?)
                            """)) {
                        psPg.setInt(1, idPagoCreado);
                        psPg.setInt(2, idReserva);
                        psPg.setString(3, hoy);
                        psPg.setDouble(4, montoPago);
                        psPg.setString(5, metodoPago);
                        if (refFinal != null) psPg.setString(6, refFinal); else psPg.setNull(6, Types.VARCHAR);
                        psPg.executeUpdate();
                    }
                }

                // Programar notificacion segun PerfilCliente
                String canalPref = "correo";
                int antHoras = 24;
                try (PreparedStatement psPerf = con.prepareStatement(
                        "SELECT canal_preferido, anticipacion_recordatorio FROM PerfilCliente WHERE id_cliente = ?")) {
                    psPerf.setInt(1, idCliente);
                    try (ResultSet rsP = psPerf.executeQuery()) {
                        if (rsP.next()) {
                            canalPref = rsP.getString("canal_preferido");
                            antHoras = rsP.getInt("anticipacion_recordatorio");
                        }
                    }
                }
                int idNotif = nextId(con, "Notificacion", "id_notificacion");
                try (PreparedStatement psN = con.prepareStatement("""
                        INSERT INTO Notificacion (id_notificacion, id_cliente, id_reserva, tipo, canal, mensaje, fecha_programada, fecha_real, estado)
                        VALUES (?, ?, ?, 'confirmacion', ?, ?, ?, ?, 'enviada')
                        """)) {
                    psN.setInt(1, idNotif);
                    psN.setInt(2, idCliente);
                    psN.setInt(3, idReserva);
                    psN.setString(4, canalPref);
                    psN.setString(5, "Reserva #" + idReserva + " registrada para " + fechaServicio + " (" + franja + ", " + comensales + " personas, " + idMesas.size() + " mesa/s acoplada/s).");
                    psN.setString(6, hoy);
                    psN.setString(7, hoy);
                    psN.executeUpdate();
                }

                int diasAntes = Math.max(1, antHoras / 24);
                LocalDate fServ = LocalDate.parse(fechaServicio);
                LocalDate fProgRec = fServ.minusDays(diasAntes);
                if (fProgRec.isBefore(LocalDate.parse(hoy))) fProgRec = LocalDate.parse(hoy);
                try (PreparedStatement psRec = con.prepareStatement("""
                        INSERT INTO Notificacion (id_notificacion, id_cliente, id_reserva, tipo, canal, mensaje, fecha_programada, fecha_real, estado)
                        VALUES (?, ?, ?, 'recordatorio', ?, ?, ?, NULL, 'pendiente')
                        """)) {
                    psRec.setInt(1, idNotif + 1);
                    psRec.setInt(2, idCliente);
                    psRec.setInt(3, idReserva);
                    psRec.setString(4, canalPref);
                    psRec.setString(5, "Recordatorio automático (" + antHoras + "h antes): su reserva #" + idReserva + " es el " + fechaServicio + ".");
                    psRec.setString(6, fProgRec.toString());
                    psRec.executeUpdate();
                }

                con.commit();
                Map<String, Object> ok = new LinkedHashMap<>();
                ok.put("ok", true);
                ok.put("id_reserva", idReserva);
                ok.put("id_pago", idPagoCreado);
                ok.put("valor_total", valorTotal);
                ok.put("capacidad_total", sumaCapacidad);
                ok.put("mesas_acopladas", idMesas.size());
                ok.put("estado", estadoInicial);
                ok.put("mensaje", "Reserva #" + idReserva + " creada (" + idMesas.size() + " mesas acopladas, " + sumaCapacidad + " sillas) con notificación por " + canalPref + ".");
                return ok;
            } catch (SQLException ex) {
                con.rollback();
                throw ex;
            } finally {
                con.setAutoCommit(true);
            }
        }
    }

    public Map<String, Object> cancelarReservaRegla80(int idReserva) throws SQLException {
        try (Connection con = ConexionBD.obtenerConexion()) {
            con.setAutoCommit(false);
            try {
                int idCliente;
                String fechaServicio;
                String estadoActual;
                try (PreparedStatement ps = con.prepareStatement(
                        "SELECT id_cliente, fecha_servicio, estado FROM Reserva WHERE id_reserva = ?")) {
                    ps.setInt(1, idReserva);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) return rollbackErr(con, "Reserva #" + idReserva + " no encontrada.");
                        idCliente = rs.getInt("id_cliente");
                        fechaServicio = rs.getString("fecha_servicio");
                        estadoActual = rs.getString("estado");
                    }
                }
                if ("cancelada".equalsIgnoreCase(estadoActual)) {
                    return rollbackErr(con, "La reserva #" + idReserva + " ya fue cancelada previamente.");
                }
                if ("completada".equalsIgnoreCase(estadoActual)) {
                    return rollbackErr(con, "No se puede cancelar una reserva ya completada.");
                }

                LocalDate hoy = LocalDate.now();
                long diasAnticipacion = ChronoUnit.DAYS.between(hoy, LocalDate.parse(fechaServicio));
                boolean aplicaReembolso80 = diasAnticipacion > 5;
                String nuevoEstadoPago = aplicaReembolso80 ? "reembolsado_80" : "retenido";

                try (PreparedStatement psUpdR = con.prepareStatement(
                        "UPDATE Reserva SET estado = 'cancelada' WHERE id_reserva = ?")) {
                    psUpdR.setInt(1, idReserva);
                    psUpdR.executeUpdate();
                }

                double sumaAprobada = 0;
                try (PreparedStatement psSum = con.prepareStatement(
                        "SELECT COALESCE(SUM(monto), 0) FROM Pago WHERE id_reserva = ? AND estado = 'aprobado'")) {
                    psSum.setInt(1, idReserva);
                    try (ResultSet rs = psSum.executeQuery()) {
                        if (rs.next()) sumaAprobada = rs.getDouble(1);
                    }
                }

                int pagosAfectados;
                try (PreparedStatement psUpdP = con.prepareStatement(
                        "UPDATE Pago SET estado = ? WHERE id_reserva = ? AND estado = 'aprobado'")) {
                    psUpdP.setString(1, nuevoEstadoPago);
                    psUpdP.setInt(2, idReserva);
                    pagosAfectados = psUpdP.executeUpdate();
                }

                double montoReembolsado = aplicaReembolso80 ? Math.round(sumaAprobada * 0.80) : 0;
                int idNotif = nextId(con, "Notificacion", "id_notificacion");
                try (PreparedStatement psN = con.prepareStatement("""
                        INSERT INTO Notificacion (id_notificacion, id_cliente, id_reserva, tipo, canal, mensaje, fecha_programada, fecha_real, estado)
                        VALUES (?, ?, ?, 'cancelacion', 'correo', ?, ?, ?, 'enviada')
                        """)) {
                    psN.setInt(1, idNotif);
                    psN.setInt(2, idCliente);
                    psN.setInt(3, idReserva);
                    psN.setString(4, "Cancelación de reserva #" + idReserva + " (" + diasAnticipacion + " días de anticipación). Estado de pago: " + nuevoEstadoPago + ". Reembolso 80%: $" + (long) montoReembolsado);
                    psN.setString(5, hoy.toString());
                    psN.setString(6, hoy.toString());
                    psN.executeUpdate();
                }

                con.commit();
                Map<String, Object> resp = new LinkedHashMap<>();
                resp.put("ok", true);
                resp.put("id_reserva", idReserva);
                resp.put("dias_anticipacion", diasAnticipacion);
                resp.put("aplica_reembolso_80", aplicaReembolso80);
                resp.put("nuevo_estado_pago", nuevoEstadoPago);
                resp.put("pagos_actualizados", pagosAfectados);
                resp.put("monto_reembolsado", montoReembolsado);
                resp.put("mensaje", aplicaReembolso80
                        ? "Reserva #" + idReserva + " cancelada con " + diasAnticipacion + " días (>5). Se aplicó reembolso del 80% ($" + (long) montoReembolsado + ")."
                        : "Reserva #" + idReserva + " cancelada con " + diasAnticipacion + " días (<=5). El monto ($" + (long) sumaAprobada + ") queda retenido sin reembolso.");
                return resp;
            } catch (SQLException ex) {
                con.rollback();
                throw ex;
            } finally {
                con.setAutoCommit(true);
            }
        }
    }

    public Map<String, Object> crearPedidoConPrecioHistorico(Map<String, Object> datos) throws SQLException {
        int idPrestador = toInt(datos.get("id_prestador"), 0);
        Integer idCliente = toNullableInt(datos.get("id_cliente"));
        Integer idReserva = toNullableInt(datos.get("id_reserva"));
        Integer idMesa = toNullableInt(datos.get("id_mesa"));
        Integer idDomiciliario = toNullableInt(datos.get("id_domiciliario"));
        String tipoPedido = strOr(datos.get("tipo_pedido"), "en_mesa");
        String direccion = str(datos.get("direccion_entrega"));
        double costoEnvio = "domicilio".equals(tipoPedido) ? Math.max(0, toDouble(datos.get("costo_envio"), 8000)) : 0;
        List<?> itemsRaw = (datos.get("items") instanceof List<?> l) ? l : List.of();

        if (idPrestador <= 0 || itemsRaw.isEmpty()) {
            return err("Debe seleccionar un restaurante y al menos un plato.");
        }
        if ("en_mesa".equals(tipoPedido) && idMesa == null) {
            return err("Regla CHECK de modalidad: un pedido 'en_mesa' exige id_mesa obligatorio.");
        }
        if ("domicilio".equals(tipoPedido) && direccion.isEmpty()) {
            return err("Regla CHECK de modalidad: un pedido a 'domicilio' exige dirección de entrega.");
        }

        try (Connection con = ConexionBD.obtenerConexion()) {
            con.setAutoCommit(false);
            try {
                int idPedido = nextId(con, "Pedido", "id_pedido");
                double totalPlatos = 0;
                List<Object[]> lineas = new ArrayList<>();
                int numLinea = 1;

                for (Object itemObj : itemsRaw) {
                    if (!(itemObj instanceof Map<?, ?> item)) continue;
                    int idPlato = toInt(item.get("id_plato"), 0);
                    int cantidad = toInt(item.get("cantidad"), 1);
                    if (idPlato <= 0 || cantidad <= 0) continue;

                    try (PreparedStatement psPl = con.prepareStatement(
                            "SELECT precio_actual, estado FROM Plato WHERE id_plato = ?")) {
                        psPl.setInt(1, idPlato);
                        try (ResultSet rsPl = psPl.executeQuery()) {
                            if (!rsPl.next()) return rollbackErr(con, "Plato #" + idPlato + " no existe.");
                            double precioHoy = rsPl.getDouble("precio_actual");
                            double subtotal = precioHoy * cantidad;
                            totalPlatos += subtotal;
                            lineas.add(new Object[]{numLinea++, idPlato, cantidad, precioHoy, subtotal});
                        }
                    }
                }

                if (lineas.isEmpty()) return rollbackErr(con, "No hay líneas válidas en el pedido.");
                double totalPedido = totalPlatos + costoEnvio;

                try (PreparedStatement psPed = con.prepareStatement("""
                        INSERT INTO Pedido (id_pedido, id_prestador, id_cliente, id_reserva, id_mesa, id_domiciliario,
                                            fecha_hora, tipo_pedido, direccion_entrega, costo_envio, total_pedido, estado_pedido)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'entregado')
                        """)) {
                    psPed.setInt(1, idPedido);
                    psPed.setInt(2, idPrestador);
                    if (idCliente != null) psPed.setInt(3, idCliente); else psPed.setNull(3, Types.INTEGER);
                    if (idReserva != null) psPed.setInt(4, idReserva); else psPed.setNull(4, Types.INTEGER);
                    if (idMesa != null) psPed.setInt(5, idMesa); else psPed.setNull(5, Types.INTEGER);
                    if (idDomiciliario != null) psPed.setInt(6, idDomiciliario); else psPed.setNull(6, Types.INTEGER);
                    psPed.setString(7, LocalDate.now().toString());
                    psPed.setString(8, tipoPedido);
                    if (!direccion.isEmpty()) psPed.setString(9, direccion); else psPed.setNull(9, Types.VARCHAR);
                    psPed.setDouble(10, costoEnvio);
                    psPed.setDouble(11, totalPedido);
                    psPed.executeUpdate();
                }

                try (PreparedStatement psDet = con.prepareStatement("""
                        INSERT INTO DetallePedido (id_pedido, num_linea, id_plato, cantidad, precio_unitario_historico, subtotal)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """)) {
                    for (Object[] ln : lineas) {
                        psDet.setInt(1, idPedido);
                        psDet.setInt(2, (Integer) ln[0]);
                        psDet.setInt(3, (Integer) ln[1]);
                        psDet.setInt(4, (Integer) ln[2]);
                        psDet.setDouble(5, (Double) ln[3]);
                        psDet.setDouble(6, (Double) ln[4]);
                        psDet.executeUpdate();
                    }
                }

                con.commit();
                return Map.of(
                        "ok", true,
                        "id_pedido", idPedido,
                        "total_pedido", totalPedido,
                        "lineas", lineas.size(),
                        "mensaje", "Pedido #" + idPedido + " guardado congelando precio_unitario_historico en " + lineas.size() + " línea(s) por $" + (long) totalPedido + "."
                );
            } catch (SQLException ex) {
                con.rollback();
                throw ex;
            } finally {
                con.setAutoCommit(true);
            }
        }
    }

    public Map<String, Object> listarNotificaciones(Integer idClienteFiltro) throws SQLException {
        String nomCli = concatEspacio("c.nombres", "c.apellidos");
        String sql = """
            SELECT n.id_notificacion, n.id_cliente, %s AS cliente,
                   n.id_reserva, n.tipo, n.canal, n.mensaje, n.fecha_programada, n.fecha_real, n.estado,
                   pc.acepta_promociones
            FROM Notificacion n
            INNER JOIN Cliente c ON n.id_cliente = c.id_cliente
            LEFT JOIN PerfilCliente pc ON c.id_cliente = pc.id_cliente
            WHERE (? IS NULL OR n.id_cliente = ?)
            ORDER BY n.id_notificacion DESC
            LIMIT 100
            """.formatted(nomCli);
        try (Connection con = ConexionBD.obtenerConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            if (idClienteFiltro == null) {
                ps.setNull(1, Types.INTEGER);
                ps.setNull(2, Types.INTEGER);
            } else {
                ps.setInt(1, idClienteFiltro);
                ps.setInt(2, idClienteFiltro);
            }
            List<Map<String, Object>> rows = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData md = rs.getMetaData();
                while (rs.next()) {
                    Map<String, Object> r = new LinkedHashMap<>();
                    for (int i = 1; i <= md.getColumnCount(); i++) {
                        r.put(md.getColumnLabel(i), rs.getObject(i));
                    }
                    rows.add(r);
                }
            }
            return Map.of("ok", true, "filas", rows);
        }
    }

    public Map<String, Object> enviarPromocionMasiva(String mensajePromo, String canalFiltro) throws SQLException {
        String msg = str(mensajePromo);
        if (msg.isEmpty()) return err("Escriba el mensaje de promoción a enviar.");

        try (Connection con = ConexionBD.obtenerConexion()) {
            con.setAutoCommit(false);
            try {
                int nextId = nextId(con, "Notificacion", "id_notificacion");
                String hoy = LocalDate.now().toString();
                String sqlClientes = """
                    SELECT c.id_cliente, pc.canal_preferido
                    FROM Cliente c
                    INNER JOIN PerfilCliente pc ON c.id_cliente = pc.id_cliente
                    WHERE pc.acepta_promociones = 1
                    ORDER BY c.id_cliente
                    LIMIT 25
                    """;
                int creadas = 0;
                try (PreparedStatement psSel = con.prepareStatement(sqlClientes);
                     ResultSet rs = psSel.executeQuery();
                     PreparedStatement psIns = con.prepareStatement("""
                        INSERT INTO Notificacion (id_notificacion, id_cliente, id_reserva, tipo, canal, mensaje, fecha_programada, fecha_real, estado)
                        VALUES (?, ?, NULL, 'promocion', ?, ?, ?, ?, 'enviada')
                        """)) {
                    while (rs.next()) {
                        String canal = (canalFiltro != null && !canalFiltro.isBlank()) ? canalFiltro : rs.getString("canal_preferido");
                        psIns.setInt(1, nextId++);
                        psIns.setInt(2, rs.getInt("id_cliente"));
                        psIns.setString(3, canal);
                        psIns.setString(4, msg);
                        psIns.setString(5, hoy);
                        psIns.setString(6, hoy);
                        psIns.executeUpdate();
                        creadas++;
                    }
                }
                con.commit();
                return Map.of(
                        "ok", true,
                        "creadas", creadas,
                        "mensaje", "Se enviaron " + creadas + " notificaciones promocionales respetando acepta_promociones = 1."
                );
            } catch (SQLException ex) {
                con.rollback();
                throw ex;
            } finally {
                con.setAutoCommit(true);
            }
        }
    }

    private static List<Map<String, Object>> queryList(Connection con, String sql) throws SQLException {
        List<Map<String, Object>> out = new ArrayList<>();
        try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            ResultSetMetaData md = rs.getMetaData();
            while (rs.next()) {
                Map<String, Object> m = new LinkedHashMap<>();
                for (int i = 1; i <= md.getColumnCount(); i++) {
                    m.put(md.getColumnLabel(i), rs.getObject(i));
                }
                out.add(m);
            }
        }
        return out;
    }

    private static int nextId(Connection con, String table, String col) throws SQLException {
        try (Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT COALESCE(MAX(" + col + "), 0) + 1 FROM " + table)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static Map<String, Object> rollbackErr(Connection con, String msg) throws SQLException {
        con.rollback();
        return err(msg);
    }

    private static Map<String, Object> err(String msg) {
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

    private static int toInt(Object o, int def) {
        if (o instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(str(o));
        } catch (Exception e) {
            return def;
        }
    }

    private static Integer toNullableInt(Object o) {
        String s = str(o);
        if (s.isEmpty() || "null".equalsIgnoreCase(s)) return null;
        try {
            return Integer.parseInt(s);
        } catch (Exception e) {
            return null;
        }
    }

    private static double toDouble(Object o, double def) {
        if (o instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(str(o));
        } catch (Exception e) {
            return def;
        }
    }

    private static String concatEspacio(String a, String b) {
        if ("sqlite".equals(ConexionBD.getEngine())) {
            return a + " || ' ' || " + b;
        }
        return "CONCAT(" + a + ", ' ', " + b + ")";
    }
}
