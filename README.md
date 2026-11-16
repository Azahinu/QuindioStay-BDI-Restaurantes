# QuindíoStay - Módulo de Restaurantes y Gastronomía (Dominio 4)

Proyecto final de Bases de Datos I (12331 - Grupo 02D)  
Programa de Ingeniería de Sistemas y Computación - Universidad del Quindío

- Estudiante: Anderson Sneider Meneses Zambrano
- Grupo de entrega: 01 (Curso 02D)
- Docente: Carolina Londoño Idárraga

## Sobre el proyecto
Aplicación web y base de datos relacional para la gestión de restaurantes, mesas, reservas, carta de platos, pedidos en mesa y domicilios en el departamento del Quindío.

- Backend en Java 21 usando `HttpServer`, patrón MVC/DAO y JDBC (`DriverManager`, `PreparedStatement`, `ResultSet`).
- Base de datos de 21 tablas y 15 vistas SQL (`vw_*`), con scripts para MySQL 8, SQL Server, Oracle y SQLite local.
- Generación de 10 reportes y 4 plantillas PDF con OpenPDF 2.0.3.
- Interfaz web con HTML, Bootstrap 5 y gráficos con Chart.js (reportes R-04, R-05 y R-06).
- Inicio de sesión por roles (`administrador`, `encargado`, `operador` en `UsuarioSistema` y `cliente` en `PerfilCliente`) con contraseñas en SHA-256.

## Estructura de carpetas

- `sql/mysql/`: scripts `01_DDL_QuindioStay.sql`, `02_DML_QuindioStay.sql` y `03_Vistas_y_Reportes_QuindioStay.sql` (motor principal en MySQL 8).
- `sql/sqlserver/`: adaptación de DDL, DML y vistas para SQL Server.
- `sql/oracle/`: adaptación de DDL, DML y vistas para Oracle.
- `sql/sqlite/`: vistas locales para pruebas rápidas con SQLite.
- `src/co/edu/uniquindio/quindiostay/`: código fuente Java (`Main`, `ConexionBD`, `ApiController`, DAOs, `GeneradorPDF`, `JsonUtil`).
- `public/index.html`: interfaz web del sistema.
- `lib/`: librerías `.jar` (OpenPDF y drivers JDBC).
- `data/quindiostay.db`: base SQLite con los datos cargados para abrir el sistema rápido.
- `docs/`: documento final, diagramas y capturas del proyecto.
- `pdf_exportados/`: ejemplos de los reportes y plantillas generados en PDF.

## Cómo ejecutarlo en Windows (JDK 21)

1. Abrir `COMPILAR.bat` (si no se ha compilado) y luego `INICIAR_SISTEMA.bat`.
2. Entrar en el navegador a `http://localhost:8080`.
3. Por defecto `config.properties` apunta a SQLite (`data/quindiostay.db`) para poder probar el sistema sin instalar un servidor aparte.
4. Si se quiere conectar a MySQL 8 o SQL Server:
   - Correr primero los 3 scripts en orden (`01`, `02` y `03`) sobre una base vacía.
   - Cambiar `db.engine` y los datos de conexión en `config.properties`.

## Usuarios de prueba

| Rol | Usuario o correo | Clave |
| :--- | :--- | :--- |
| `administrador` | `admin_quindio` | `Quindio2026*` |
| `encargado` | `encargado_cocora` | `Quindio2026*` |
| `operador` | `operador_salento` | `Quindio2026*` |
| `cliente` | `valentina.arango1@correo.com` | `Cliente2026*` |
