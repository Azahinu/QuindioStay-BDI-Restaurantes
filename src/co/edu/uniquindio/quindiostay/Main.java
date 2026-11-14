package co.edu.uniquindio.quindiostay;

import co.edu.uniquindio.quindiostay.config.ConexionBD;
import co.edu.uniquindio.quindiostay.controller.ApiController;
import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.sql.Connection;
import java.util.concurrent.Executors;

/**
 * Punto de entrada de la aplicacion web QuindioStay (Dominio 4: Restaurantes) en Java 21.
 * Arquitectura MVC + DAO + JDBC (PreparedStatement/ResultSet) + Vistas SQL + OpenPDF.
 */
public class Main {

    public static void main(String[] args) throws Exception {
        ConexionBD.inicializarVistasSiEsSQLite();
        try (Connection con = ConexionBD.obtenerConexion()) {
            System.out.println("=================================================================");
            System.out.println(" QuindioStay - Dominio 4: Restaurantes (Java JDK 21 + JDBC + OpenPDF)");
            System.out.println(" Motor activo : " + ConexionBD.getEngine().toUpperCase() + " (" + con.getMetaData().getDatabaseProductName() + ")");
        }

        int port = ConexionBD.getServerPort();
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", new ApiController());
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.start();

        System.out.println(" Servidor web escuchando en: http://localhost:" + port);
        System.out.println(" Credenciales demo (UsuarioSistema): admin_quindio / Quindio2026*");
        System.out.println(" Credenciales demo (Encargado)     : encargado_cocora / Quindio2026*");
        System.out.println(" Credenciales demo (Cliente Web)   : valentina.arango1@correo.com / Cliente2026*");
        System.out.println("=================================================================");
    }
}
