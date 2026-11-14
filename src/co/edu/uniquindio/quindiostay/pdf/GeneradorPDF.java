package co.edu.uniquindio.quindiostay.pdf;

import co.edu.uniquindio.quindiostay.config.ConexionBD;
import co.edu.uniquindio.quindiostay.dao.ReporteDAO.ReporteResultado;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

/**
 * Generador real de archivos PDF binarios desde Java usando OpenPDF 2.0.3 (Entrega 2 / Clase 18).
 */
public class GeneradorPDF {

    private static final Color VERDE_UQ = new Color(20, 83, 45);
    private static final Color VERDE_SUAVE = new Color(240, 253, 244);
    private static final Color GRIS_BORDE = new Color(203, 213, 225);
    private static final Color TEXTO_SUAVE = new Color(71, 85, 105);

    private static final Font F_TITULO = new Font(Font.HELVETICA, 15, Font.BOLD, VERDE_UQ);
    private static final Font F_SUBTITULO = new Font(Font.HELVETICA, 10, Font.BOLD, Color.DARK_GRAY);
    private static final Font F_META = new Font(Font.HELVETICA, 8.5f, Font.NORMAL, TEXTO_SUAVE);
    private static final Font F_TH = new Font(Font.HELVETICA, 8.5f, Font.BOLD, Color.WHITE);
    private static final Font F_TD = new Font(Font.HELVETICA, 8f, Font.NORMAL, Color.BLACK);
    private static final Font F_TD_BOLD = new Font(Font.HELVETICA, 8.5f, Font.BOLD, Color.BLACK);

    public byte[] generarReportePDF(ReporteResultado res) throws DocumentException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Rectangle size = res.columnas().size() > 6 ? PageSize.A4.rotate() : PageSize.A4;
        Document doc = new Document(size, 28, 28, 32, 32);
        PdfWriter.getInstance(doc, baos);
        doc.open();

        agregarEncabezadoInstitucional(doc, res.titulo(),
                "Nivel: " + res.nivel() + "  |  Vista SQL: " + res.vistaSql() + "  |  Filas: " + res.filas().size());

        Paragraph sqlBox = new Paragraph("Consulta ejecutada (PreparedStatement): " + res.sqlEjecutado(), F_META);
        sqlBox.setSpacingAfter(10);
        doc.add(sqlBox);

        if (res.columnas().isEmpty() || res.filas().isEmpty()) {
            doc.add(new Paragraph("La consulta no arrojó resultados con los filtros actuales.", F_SUBTITULO));
        } else {
            PdfPTable table = new PdfPTable(res.columnas().size());
            table.setWidthPercentage(100);
            table.setHeaderRows(1);

            for (String col : res.columnas()) {
                PdfPCell th = new PdfPCell(new Phrase(col.replace('_', ' ').toUpperCase(), F_TH));
                th.setBackgroundColor(VERDE_UQ);
                th.setPadding(6);
                th.setBorderColor(GRIS_BORDE);
                table.addCell(th);
            }

            int idx = 0;
            for (Map<String, Object> fila : res.filas()) {
                Color bg = (idx++ % 2 == 1) ? VERDE_SUAVE : Color.WHITE;
                for (String col : res.columnas()) {
                    Object val = fila.get(col);
                    PdfPCell td = new PdfPCell(new Phrase(val == null ? "" : formatearValor(col, val), F_TD));
                    td.setBackgroundColor(bg);
                    td.setPadding(5);
                    td.setBorderColor(GRIS_BORDE);
                    if (val instanceof Number && !col.startsWith("id_")) {
                        td.setHorizontalAlignment(Element.ALIGN_RIGHT);
                    }
                    table.addCell(td);
                }
            }
            doc.add(table);
        }

        agregarPieDocumento(doc);
        doc.close();
        return baos.toByteArray();
    }

    public byte[] generarPlantillaComprobanteReservaPDF(int idReserva) throws SQLException, DocumentException {
        String sql = """
            SELECT r.id_reserva, r.fecha_creacion, r.fecha_servicio, r.franja_horaria,
                   r.numero_comensales, r.valor_total, r.canal, r.estado,
                   c.tipo_documento, c.numero_documento, c.nombres, c.apellidos, c.correo, c.telefono,
                   COALESCE(pl.nombre, ex.nombre_experiencia, 'Solo mesa') AS servicio,
                   pr.nombre_comercial, pr.nit_documento, pr.direccion, m.nombre AS municipio
            FROM Reserva r
            INNER JOIN Cliente c ON r.id_cliente = c.id_cliente
            LEFT JOIN Plan pl ON r.id_plan = pl.id_plan
            LEFT JOIN Experiencia ex ON r.id_experiencia = ex.id_experiencia
            LEFT JOIN Prestador pr ON pr.id_prestador = COALESCE(pl.id_prestador, ex.id_prestador)
            LEFT JOIN Municipio m ON pr.codigo_dane = m.codigo_dane
            WHERE r.id_reserva = ?
            """;

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 36, 36, 36, 36);
        PdfWriter.getInstance(doc, baos);
        doc.open();

        try (Connection con = ConexionBD.obtenerConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, idReserva);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalArgumentException("No existe la reserva #" + idReserva);
                }
                agregarEncabezadoInstitucional(doc, "COMPROBANTE OFICIAL DE RESERVA #" + idReserva,
                        "Documento generado en vivo mediante JDBC + OpenPDF · Dominio 4: Restaurantes");

                PdfPTable info = new PdfPTable(2);
                info.setWidthPercentage(100);
                info.setSpacingAfter(12);
                agregarParClaveValor(info, "Restaurante / Prestador", rs.getString("nombre_comercial") + " (" + rs.getString("municipio") + ")");
                agregarParClaveValor(info, "Dirección / NIT", rs.getString("direccion") + " · NIT " + rs.getString("nit_documento"));
                agregarParClaveValor(info, "Titular de la reserva", rs.getString("nombres") + " " + rs.getString("apellidos"));
                agregarParClaveValor(info, "Documento / Contacto", rs.getString("tipo_documento") + " " + rs.getString("numero_documento") + " · Tel: " + rs.getString("telefono"));
                agregarParClaveValor(info, "Plan / Experiencia", rs.getString("servicio"));
                agregarParClaveValor(info, "Fecha servicio y franja", rs.getString("fecha_servicio") + " · " + rs.getString("franja_horaria").toUpperCase());
                agregarParClaveValor(info, "Comensales / Canal", rs.getInt("numero_comensales") + " personas · Canal: " + rs.getString("canal"));
                agregarParClaveValor(info, "Valor total / Estado", moneda(rs.getDouble("valor_total")) + " · Estado: " + rs.getString("estado").toUpperCase());
                doc.add(info);
            }

            Paragraph sub = new Paragraph("Mesas Físicas Asignadas (Tabla Puente ReservaMesa)", F_SUBTITULO);
            sub.setSpacingAfter(6);
            doc.add(sub);

            PdfPTable mesasTbl = new PdfPTable(4);
            mesasTbl.setWidthPercentage(100);
            mesasTbl.setHeaderRows(1);
            for (String h : new String[]{"ID Mesa", "Número de Mesa", "Zona Física", "Capacidad (Sillas)"}) {
                PdfPCell c = new PdfPCell(new Phrase(h, F_TH));
                c.setBackgroundColor(VERDE_UQ);
                c.setPadding(6);
                mesasTbl.addCell(c);
            }
            int capTotal = 0;
            try (PreparedStatement psM = con.prepareStatement(
                    "SELECT m.id_mesa, m.numero_mesa, m.zona, m.capacidad FROM ReservaMesa rm JOIN Mesa m ON rm.id_mesa=m.id_mesa WHERE rm.id_reserva = ? ORDER BY m.numero_mesa")) {
                psM.setInt(1, idReserva);
                try (ResultSet rsM = psM.executeQuery()) {
                    while (rsM.next()) {
                        mesasTbl.addCell(celda(String.valueOf(rsM.getInt("id_mesa"))));
                        mesasTbl.addCell(celda("Mesa #" + rsM.getInt("numero_mesa")));
                        mesasTbl.addCell(celda(rsM.getString("zona")));
                        int cap = rsM.getInt("capacidad");
                        capTotal += cap;
                        mesasTbl.addCell(celda(cap + " sillas"));
                    }
                }
            }
            doc.add(mesasTbl);

            Paragraph resumenCap = new Paragraph("Capacidad acumulada asignada: " + capTotal + " sillas. Regla de cancelación: reembolso del 80% si cancela con más de 5 días de anticipación.", F_META);
            resumenCap.setSpacingBefore(10);
            doc.add(resumenCap);
        }

        agregarPieDocumento(doc);
        doc.close();
        return baos.toByteArray();
    }

    public byte[] generarPlantillaComprobantePagoPDF(int idPago) throws SQLException, DocumentException {
        String sql = """
            SELECT pg.id_pago, pg.id_reserva, pg.fecha_pago, pg.monto, pg.metodo, pg.estado, pg.referencia_transaccion,
                   r.fecha_servicio, r.franja_horaria, r.valor_total,
                   c.tipo_documento, c.numero_documento, c.nombres, c.apellidos, c.correo
            FROM Pago pg
            INNER JOIN Reserva r ON pg.id_reserva = r.id_reserva
            INNER JOIN Cliente c ON r.id_cliente = c.id_cliente
            WHERE pg.id_pago = ?
            """;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 36, 36, 36, 36);
        PdfWriter.getInstance(doc, baos);
        doc.open();

        try (Connection con = ConexionBD.obtenerConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, idPago);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("No existe el pago #" + idPago);
                agregarEncabezadoInstitucional(doc, "COMPROBANTE DE PAGO Y TRANSACCIÓN #" + idPago,
                        "Pasarela QuindíoStay · Referencia trazable de reserva #" + rs.getInt("id_reserva"));

                PdfPTable info = new PdfPTable(2);
                info.setWidthPercentage(100);
                agregarParClaveValor(info, "Titular pagador", rs.getString("nombres") + " " + rs.getString("apellidos") + " (" + rs.getString("tipo_documento") + " " + rs.getString("numero_documento") + ")");
                agregarParClaveValor(info, "Correo electrónico", rs.getString("correo"));
                agregarParClaveValor(info, "Reserva asociada", "Reserva #" + rs.getInt("id_reserva") + " (" + rs.getString("fecha_servicio") + " · " + rs.getString("franja_horaria") + ")");
                agregarParClaveValor(info, "Fecha y método de pago", rs.getString("fecha_pago") + " · " + rs.getString("metodo").toUpperCase());
                agregarParClaveValor(info, "Referencia pasarela", rs.getString("referencia_transaccion") == null ? "Pago en efectivo en caja" : rs.getString("referencia_transaccion"));
                agregarParClaveValor(info, "Monto transacción", moneda(rs.getDouble("monto")) + " (Valor reserva: " + moneda(rs.getDouble("valor_total")) + ")");
                agregarParClaveValor(info, "Estado del pago", rs.getString("estado").toUpperCase());
                doc.add(info);
            }
        }

        agregarPieDocumento(doc);
        doc.close();
        return baos.toByteArray();
    }

    public byte[] generarPlantillaFacturaPedidoPDF(int idPedido) throws SQLException, DocumentException {
        String sqlCab = """
            SELECT pe.id_pedido, pe.fecha_hora, pe.tipo_pedido, pe.direccion_entrega, pe.costo_envio,
                   pe.total_pedido, pe.estado_pedido,
                   pr.nombre_comercial, pr.nit_documento, pr.direccion, m.nombre AS municipio,
                   c.nombres, c.apellidos, c.numero_documento
            FROM Pedido pe
            INNER JOIN Prestador pr ON pe.id_prestador = pr.id_prestador
            INNER JOIN Municipio m ON pr.codigo_dane = m.codigo_dane
            LEFT JOIN Cliente c ON pe.id_cliente = c.id_cliente
            WHERE pe.id_pedido = ?
            """;
        String sqlDet = """
            SELECT dp.num_linea, pl.nombre_plato, dp.cantidad, dp.precio_unitario_historico,
                   pl.precio_actual, dp.subtotal
            FROM DetallePedido dp
            INNER JOIN Plato pl ON dp.id_plato = pl.id_plato
            WHERE dp.id_pedido = ?
            ORDER BY dp.num_linea
            """;

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 36, 36, 36, 36);
        PdfWriter.getInstance(doc, baos);
        doc.open();

        try (Connection con = ConexionBD.obtenerConexion();
             PreparedStatement ps = con.prepareStatement(sqlCab)) {
            ps.setInt(1, idPedido);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("No existe el pedido #" + idPedido);
                agregarEncabezadoInstitucional(doc, "FACTURA DE CONSUMO GASTRONÓMICO - PEDIDO #" + idPedido,
                        rs.getString("nombre_comercial") + " · " + rs.getString("municipio") + " · NIT " + rs.getString("nit_documento"));

                PdfPTable cab = new PdfPTable(2);
                cab.setWidthPercentage(100);
                cab.setSpacingAfter(12);
                String nomCli = rs.getString("nombres") != null
                        ? rs.getString("nombres") + " " + rs.getString("apellidos") + " (Doc: " + rs.getString("numero_documento") + ")"
                        : "Cliente de Mostrador";
                agregarParClaveValor(cab, "Cliente", nomCli);
                agregarParClaveValor(cab, "Fecha / Modalidad", rs.getString("fecha_hora") + " · " + rs.getString("tipo_pedido"));
                agregarParClaveValor(cab, "Dirección entrega", rs.getString("direccion_entrega") == null ? rs.getString("direccion") + " (Local)" : rs.getString("direccion_entrega"));
                agregarParClaveValor(cab, "Costo envío / Estado", moneda(rs.getDouble("costo_envio")) + " · Estado: " + rs.getString("estado_pedido").toUpperCase());
                doc.add(cab);

                PdfPTable det = new PdfPTable(new float[]{0.8f, 3.4f, 1f, 1.8f, 1.8f, 1.8f});
                det.setWidthPercentage(100);
                det.setHeaderRows(1);
                for (String h : new String[]{"Línea", "Plato", "Cant.", "Precio Histórico", "Carta Actual", "Subtotal"}) {
                    PdfPCell c = new PdfPCell(new Phrase(h, F_TH));
                    c.setBackgroundColor(VERDE_UQ);
                    c.setPadding(6);
                    det.addCell(c);
                }
                try (PreparedStatement psDet = con.prepareStatement(sqlDet)) {
                    psDet.setInt(1, idPedido);
                    try (ResultSet rsD = psDet.executeQuery()) {
                        while (rsD.next()) {
                            det.addCell(celda(String.valueOf(rsD.getInt("num_linea"))));
                            det.addCell(celda(rsD.getString("nombre_plato")));
                            det.addCell(celda(String.valueOf(rsD.getInt("cantidad"))));
                            det.addCell(celda(moneda(rsD.getDouble("precio_unitario_historico"))));
                            det.addCell(celda(moneda(rsD.getDouble("precio_actual"))));
                            det.addCell(celda(moneda(rsD.getDouble("subtotal"))));
                        }
                    }
                }
                doc.add(det);

                Paragraph tot = new Paragraph("TOTAL FACTURADO (incluye envío): " + moneda(rs.getDouble("total_pedido")), F_TITULO);
                tot.setAlignment(Element.ALIGN_RIGHT);
                tot.setSpacingBefore(10);
                doc.add(tot);

                Paragraph nota = new Paragraph("Nota de Auditoría Contable: El valor cobrado corresponde exactamente al atributo congelado DetallePedido.precio_unitario_historico del día de la comanda.", F_META);
                nota.setSpacingBefore(6);
                doc.add(nota);
            }
        }

        agregarPieDocumento(doc);
        doc.close();
        return baos.toByteArray();
    }

    public byte[] generarPlantillaAutorizacionDatosPDF(int idCliente) throws SQLException, DocumentException {
        String sql = """
            SELECT c.id_cliente, c.tipo_documento, c.numero_documento, c.nombres, c.apellidos,
                   c.correo, c.telefono, c.ciudad_origen,
                   pc.idioma, pc.canal_preferido, pc.anticipacion_recordatorio, pc.acepta_promociones
            FROM Cliente c
            LEFT JOIN PerfilCliente pc ON c.id_cliente = pc.id_cliente
            WHERE c.id_cliente = ?
            """;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 36, 36, 36, 36);
        PdfWriter.getInstance(doc, baos);
        doc.open();

        try (Connection con = ConexionBD.obtenerConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, idCliente);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("No existe el cliente #" + idCliente);
                agregarEncabezadoInstitucional(doc, "AUTORIZACIÓN DE TRATAMIENTO DE DATOS PERSONALES (LEY 1581 DE 2012)",
                        "Plataforma Turística y Gastronómica QuindíoStay · Consentimiento Informado del Titular");

                PdfPTable info = new PdfPTable(2);
                info.setWidthPercentage(100);
                info.setSpacingAfter(12);
                agregarParClaveValor(info, "Titular de los datos", rs.getString("nombres") + " " + rs.getString("apellidos"));
                agregarParClaveValor(info, "Identificación", rs.getString("tipo_documento") + " " + rs.getString("numero_documento"));
                agregarParClaveValor(info, "Correo / Teléfono", rs.getString("correo") + " · " + rs.getString("telefono"));
                agregarParClaveValor(info, "Ciudad de origen", rs.getString("ciudad_origen"));
                agregarParClaveValor(info, "Canal preferido avisos", rs.getString("canal_preferido") != null ? rs.getString("canal_preferido") : "correo");
                agregarParClaveValor(info, "Anticipación recordatorio", (rs.getObject("anticipacion_recordatorio") != null ? rs.getInt("anticipacion_recordatorio") : 24) + " horas antes");
                boolean acepta = rs.getObject("acepta_promociones") != null && rs.getInt("acepta_promociones") == 1;
                agregarParClaveValor(info, "Autoriza promociones", acepta ? "SÍ AUTORIZA envío de promociones gastronómicas" : "NO AUTORIZA envío de promociones (solo transaccionales)");
                doc.add(info);

                Paragraph textoLegal = new Paragraph(
                        "En cumplimiento de la Ley Estatutaria 1581 de 2012 y el Decreto 1377 de 2013, el titular autoriza de manera previa, expresa e informada a QuindíoStay y a sus prestadores gastronómicos del Departamento del Quindío para almacenar y procesar sus datos de contacto con fines de confirmación de reservas, facturación electrónica, recordatorios de mesa y, únicamente si la casilla superior indica SÍ AUTORIZA, envío de promociones turísticas.",
                        F_TD);
                textoLegal.setSpacingAfter(18);
                doc.add(textoLegal);

                Paragraph firma = new Paragraph("Firma digital / Aceptación registrada en PerfilCliente (ID #" + idCliente + "): _______________________________", F_SUBTITULO);
                doc.add(firma);
            }
        }

        agregarPieDocumento(doc);
        doc.close();
        return baos.toByteArray();
    }

    private static void agregarEncabezadoInstitucional(Document doc, String titulo, String subtitulo) throws DocumentException {
        Paragraph inst = new Paragraph("UNIVERSIDAD DEL QUINDÍO · BASES DE DATOS I · PLATAFORMA QUINDÍOSTAY", F_META);
        inst.setSpacingAfter(4);
        doc.add(inst);
        Paragraph t = new Paragraph(titulo, F_TITULO);
        t.setSpacingAfter(4);
        doc.add(t);
        Paragraph s = new Paragraph(subtitulo, F_SUBTITULO);
        s.setSpacingAfter(10);
        doc.add(s);
    }

    private static void agregarPieDocumento(Document doc) throws DocumentException {
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        Paragraph pie = new Paragraph("\nGenerado en vivo desde Java (JDK 21 + OpenPDF 2.0.3) el " + ts, F_META);
        pie.setAlignment(Element.ALIGN_RIGHT);
        doc.add(pie);
    }

    private static void agregarParClaveValor(PdfPTable tbl, String k, String v) {
        PdfPCell c1 = new PdfPCell(new Phrase(k, F_TD_BOLD));
        c1.setBackgroundColor(VERDE_SUAVE);
        c1.setPadding(6);
        c1.setBorderColor(GRIS_BORDE);
        PdfPCell c2 = new PdfPCell(new Phrase(v == null ? "" : v, F_TD));
        c2.setPadding(6);
        c2.setBorderColor(GRIS_BORDE);
        tbl.addCell(c1);
        tbl.addCell(c2);
    }

    private static PdfPCell celda(String txt) {
        PdfPCell c = new PdfPCell(new Phrase(txt == null ? "" : txt, F_TD));
        c.setPadding(5);
        c.setBorderColor(GRIS_BORDE);
        return c;
    }

    private static String moneda(double v) {
        NumberFormat nf = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-CO"));
        nf.setMaximumFractionDigits(0);
        return nf.format(v);
    }

    private static String formatearValor(String col, Object val) {
        if (val instanceof Number n) {
            String low = col.toLowerCase();
            if (low.contains("precio") || low.contains("total") || low.contains("ingreso") || low.contains("gast")
                    || low.contains("recaudo") || low.contains("ticket") || low.contains("monto")
                    || low.contains("reembolsado") || low.contains("retenido") || low.contains("promedio")
                    || low.contains("envio")) {
                return moneda(n.doubleValue());
            }
        }
        return String.valueOf(val);
    }
}
