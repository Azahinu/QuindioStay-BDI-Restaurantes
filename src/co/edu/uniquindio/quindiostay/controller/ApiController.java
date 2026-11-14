package co.edu.uniquindio.quindiostay.controller;

import co.edu.uniquindio.quindiostay.config.ConexionBD;
import co.edu.uniquindio.quindiostay.dao.AuthDAO;
import co.edu.uniquindio.quindiostay.dao.CrudDAO;
import co.edu.uniquindio.quindiostay.dao.NegocioDAO;
import co.edu.uniquindio.quindiostay.dao.ReporteDAO;
import co.edu.uniquindio.quindiostay.dao.ReporteDAO.ReporteResultado;
import co.edu.uniquindio.quindiostay.pdf.GeneradorPDF;
import co.edu.uniquindio.quindiostay.util.JsonUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Controlador MVC central sobre com.sun.net.httpserver.HttpServer (Java 21).
 */
public class ApiController implements HttpHandler {

    private final AuthDAO authDAO = new AuthDAO();
    private final CrudDAO crudDAO = new CrudDAO();
    private final NegocioDAO negocioDAO = new NegocioDAO();
    private final ReporteDAO reporteDAO = new ReporteDAO();
    private final GeneradorPDF pdfGen = new GeneradorPDF();
    private final Map<String, Map<String, Object>> sesiones = new ConcurrentHashMap<>();

    @Override
    public void handle(HttpExchange ex) throws IOException {
        URI uri = ex.getRequestURI();
        String path = uri.getPath();
        String method = ex.getRequestMethod();
        Map<String, String> query = parseQuery(uri.getRawQuery());

        try {
            if (!path.startsWith("/api/")) {
                servirEstatico(ex, path);
                return;
            }

            if ("/api/login".equals(path) && "POST".equals(method)) {
                Map<String, Object> body = JsonUtil.parseObject(readBody(ex));
                String tipo = str(body.get("tipoCuenta"));
                String user = str(body.get("usuario"));
                String pass = str(body.get("contrasena"));
                Map<String, Object> auth = authDAO.autenticar(tipo, user, pass);
                if (!Boolean.TRUE.equals(auth.get("ok"))) {
                    sendJson(ex, 401, auth);
                    return;
                }
                String token = UUID.randomUUID().toString();
                sesiones.put(token, auth);
                ex.getResponseHeaders().add("Set-Cookie", "QS_SID=" + token + "; Path=/; HttpOnly");
                sendJson(ex, 200, auth);
                return;
            }

            if ("/api/logout".equals(path) && "POST".equals(method)) {
                String sid = extraerCookie(ex, "QS_SID");
                if (sid != null) sesiones.remove(sid);
                ex.getResponseHeaders().add("Set-Cookie", "QS_SID=; Path=/; Max-Age=0");
                sendJson(ex, 200, Map.of("ok", true));
                return;
            }

            if ("/api/sesion".equals(path) && "GET".equals(method)) {
                Map<String, Object> ses = obtenerSesion(ex);
                if (ses == null) {
                    sendJson(ex, 200, Map.of("ok", false, "autenticado", false, "motor", ConexionBD.getEngine()));
                } else {
                    Map<String, Object> copy = new LinkedHashMap<>(ses);
                    copy.put("autenticado", true);
                    copy.put("motor", ConexionBD.getEngine());
                    sendJson(ex, 200, copy);
                }
                return;
            }

            if ("/api/clientes/registro".equals(path) && "POST".equals(method)) {
                Map<String, Object> body = JsonUtil.parseObject(readBody(ex));
                sendJson(ex, 200, authDAO.registrarCliente(body));
                return;
            }

            if ("/api/perfil".equals(path)) {
                Map<String, Object> ses = obtenerSesion(ex);
                int idCli = query.containsKey("id_cliente")
                        ? parseInt(query.get("id_cliente"), 1)
                        : (ses != null && ses.get("idCliente") instanceof Number n ? n.intValue() : 1);
                if ("GET".equals(method)) {
                    sendJson(ex, 200, authDAO.obtenerPerfilCliente(idCli));
                } else if ("PUT".equals(method)) {
                    Map<String, Object> body = JsonUtil.parseObject(readBody(ex));
                    int targetId = body.get("id_cliente") instanceof Number n ? n.intValue() : idCli;
                    sendJson(ex, 200, authDAO.actualizarPerfilCliente(targetId, body));
                } else {
                    sendJson(ex, 405, Map.of("ok", false, "error", "Método no permitido"));
                }
                return;
            }

            if ("/api/resumen".equals(path) && "GET".equals(method)) {
                sendJson(ex, 200, crudDAO.obtenerResumenTablas());
                return;
            }

            if ("/api/tabla".equals(path)) {
                Map<String, Object> ses = obtenerSesion(ex);
                String tabla = query.getOrDefault("nombre", "Plato");
                switch (method) {
                    case "GET" -> {
                        Integer scope = null;
                        if (ses != null && Boolean.parseBoolean(query.getOrDefault("soloMiLocal", "false"))) {
                            if (ses.get("idPrestador") instanceof Number n) scope = n.intValue();
                        }
                        sendJson(ex, 200, crudDAO.listarTabla(tabla, query.getOrDefault("q", ""), scope));
                    }
                    case "POST" -> {
                        Map<String, Object> body = JsonUtil.parseObject(readBody(ex));
                        sendJson(ex, 200, crudDAO.insertarFila(tabla, body));
                    }
                    case "PUT" -> {
                        Map<String, Object> body = JsonUtil.parseObject(readBody(ex));
                        sendJson(ex, 200, crudDAO.actualizarFila(tabla, body));
                    }
                    case "DELETE" -> {
                        Map<String, Object> body = JsonUtil.parseObject(readBody(ex));
                        sendJson(ex, 200, crudDAO.eliminarFila(tabla, body));
                    }
                    default -> sendJson(ex, 405, Map.of("ok", false, "error", "Método no soportado"));
                }
                return;
            }

            if ("/api/plato/etiquetas".equals(path)) {
                if ("GET".equals(method)) {
                    int idPlato = parseInt(query.get("id_plato"), 0);
                    sendJson(ex, 200, crudDAO.obtenerEtiquetasPlato(idPlato));
                } else if ("PUT".equals(method)) {
                    Map<String, Object> body = JsonUtil.parseObject(readBody(ex));
                    int idPlato = parseInt(String.valueOf(body.get("id_plato")), 0);
                    List<?> tags = body.get("etiquetas") instanceof List<?> l ? l : List.of();
                    sendJson(ex, 200, crudDAO.guardarEtiquetasPlato(idPlato, tags));
                }
                return;
            }

            if ("/api/catalogo".equals(path) && "GET".equals(method)) {
                Map<String, Object> ses = obtenerSesion(ex);
                Integer scope = (ses != null && ses.get("idPrestador") instanceof Number n && !"administrador".equals(ses.get("rol")))
                        ? n.intValue() : null;
                sendJson(ex, 200, negocioDAO.obtenerCatalogoOperativo(scope));
                return;
            }

            if ("/api/reservas/crear".equals(path) && "POST".equals(method)) {
                Map<String, Object> body = JsonUtil.parseObject(readBody(ex));
                sendJson(ex, 200, negocioDAO.crearReservaCompleta(body));
                return;
            }

            if ("/api/reservas/cancelar".equals(path) && "POST".equals(method)) {
                Map<String, Object> body = JsonUtil.parseObject(readBody(ex));
                int idRes = parseInt(String.valueOf(body.get("id_reserva")), 0);
                sendJson(ex, 200, negocioDAO.cancelarReservaRegla80(idRes));
                return;
            }

            if ("/api/pedidos/crear".equals(path) && "POST".equals(method)) {
                Map<String, Object> body = JsonUtil.parseObject(readBody(ex));
                sendJson(ex, 200, negocioDAO.crearPedidoConPrecioHistorico(body));
                return;
            }

            if ("/api/notificaciones".equals(path) && "GET".equals(method)) {
                Integer idCli = query.containsKey("id_cliente") && !query.get("id_cliente").isBlank()
                        ? parseInt(query.get("id_cliente"), 0) : null;
                sendJson(ex, 200, negocioDAO.listarNotificaciones(idCli));
                return;
            }

            if ("/api/notificaciones/promocion".equals(path) && "POST".equals(method)) {
                Map<String, Object> body = JsonUtil.parseObject(readBody(ex));
                sendJson(ex, 200, negocioDAO.enviarPromocionMasiva(str(body.get("mensaje")), str(body.get("canal"))));
                return;
            }

            if ("/api/reportes".equals(path) && "GET".equals(method)) {
                sendJson(ex, 200, Map.of("ok", true, "reportes", reporteDAO.listarMetadatosReportes()));
                return;
            }

            if ("/api/reporte".equals(path) && "GET".equals(method)) {
                String cod = query.getOrDefault("codigo", "R-01");
                ReporteResultado res = reporteDAO.ejecutarReporte(cod, query);
                sendJson(ex, 200, res.toMap());
                return;
            }

            if ("/api/reporte/pdf".equals(path) && "GET".equals(method)) {
                String cod = query.getOrDefault("codigo", "R-01");
                ReporteResultado res = reporteDAO.ejecutarReporte(cod, query);
                byte[] pdf = pdfGen.generarReportePDF(res);
                sendPdf(ex, "Reporte_" + cod + "_QuindioStay.pdf", pdf);
                return;
            }

            if ("/api/plantilla/pdf".equals(path) && "GET".equals(method)) {
                String tipo = query.getOrDefault("tipo", "reserva");
                int id = parseInt(query.get("id"), 502);
                byte[] pdf;
                String filename;
                switch (tipo) {
                    case "reserva" -> {
                        pdf = pdfGen.generarPlantillaComprobanteReservaPDF(id);
                        filename = "Plantilla_Comprobante_Reserva_" + id + ".pdf";
                    }
                    case "pago" -> {
                        pdf = pdfGen.generarPlantillaComprobantePagoPDF(id);
                        filename = "Plantilla_Comprobante_Pago_" + id + ".pdf";
                    }
                    case "factura" -> {
                        pdf = pdfGen.generarPlantillaFacturaPedidoPDF(id);
                        filename = "Plantilla_Factura_Pedido_" + id + ".pdf";
                    }
                    case "autorizacion" -> {
                        pdf = pdfGen.generarPlantillaAutorizacionDatosPDF(id);
                        filename = "Plantilla_Autorizacion_Datos_" + id + ".pdf";
                    }
                    default -> throw new IllegalArgumentException("Tipo de plantilla inválido: " + tipo);
                }
                sendPdf(ex, filename, pdf);
                return;
            }

            if ("/api/sql".equals(path) && "POST".equals(method)) {
                Map<String, Object> body = JsonUtil.parseObject(readBody(ex));
                String sql = str(body.get("sql"));
                ReporteResultado res = reporteDAO.ejecutarConsultaLibre(sql);
                sendJson(ex, 200, res.toMap());
                return;
            }

            sendJson(ex, 404, Map.of("ok", false, "error", "Endpoint no encontrado: " + path));
        } catch (SQLException sqle) {
            sendJson(ex, 400, Map.of("ok", false, "error", ConexionBD.traducirErrorSQL(sqle)));
        } catch (Exception e) {
            sendJson(ex, 400, Map.of("ok", false, "error", e.getMessage() != null ? e.getMessage() : "Error interno"));
        }
    }

    private Map<String, Object> obtenerSesion(HttpExchange ex) {
        String sid = extraerCookie(ex, "QS_SID");
        return sid == null ? null : sesiones.get(sid);
    }

    private static void servirEstatico(HttpExchange ex, String reqPath) throws IOException {
        Path base = Path.of("public").toAbsolutePath().normalize();
        String rel = ("/".equals(reqPath) || reqPath.isEmpty()) ? "index.html" : reqPath.substring(1);
        Path target = base.resolve(rel).normalize();
        if (!target.startsWith(base) || !Files.exists(target) || Files.isDirectory(target)) {
            byte[] notFound = "Archivo no encontrado".getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(404, notFound.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(notFound);
            }
            return;
        }
        String contentType = rel.endsWith(".html") ? "text/html; charset=utf-8"
                : rel.endsWith(".css") ? "text/css; charset=utf-8"
                : rel.endsWith(".js") ? "application/javascript; charset=utf-8"
                : "application/octet-stream";
        byte[] bytes = Files.readAllBytes(target);
        ex.getResponseHeaders().set("Content-Type", contentType);
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static void sendJson(HttpExchange ex, int status, Object obj) throws IOException {
        byte[] bytes = JsonUtil.toJson(obj).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static void sendPdf(HttpExchange ex, String filename, byte[] bytes) throws IOException {
        ex.getResponseHeaders().set("Content-Type", "application/pdf");
        ex.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String readBody(HttpExchange ex) throws IOException {
        return new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    private static Map<String, String> parseQuery(String raw) {
        Map<String, String> map = new LinkedHashMap<>();
        if (raw == null || raw.isBlank()) return map;
        for (String pair : raw.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                String k = URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8);
                String v = URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
                map.put(k, v);
            }
        }
        return map;
    }

    private static String extraerCookie(HttpExchange ex, String name) {
        List<String> cookies = ex.getRequestHeaders().get("Cookie");
        if (cookies == null) return null;
        for (String header : cookies) {
            for (String part : header.split(";")) {
                String[] kv = part.trim().split("=", 2);
                if (kv.length == 2 && kv[0].trim().equals(name)) {
                    return kv[1].trim();
                }
            }
        }
        return null;
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }

    private static int parseInt(String s, int def) {
        try {
            return (s == null || s.isBlank()) ? def : Integer.parseInt(s.trim());
        } catch (Exception e) {
            return def;
        }
    }
}
