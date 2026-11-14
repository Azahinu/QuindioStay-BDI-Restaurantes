# QuindíoStay — Módulo Restaurantes y Gastronomía (Dominio 4)

Proyecto Final de **Bases de Datos I (12331 - Grupo 02D)** — Programa de Ingeniería de Sistemas y Computación, **Universidad del Quindío**.  
**Autor:** Anderson Sneider Meneses Zambrano · **Docente:** Carolina Londoño Idárraga

---

## Arquitectura del Sistema
- **Lenguaje y Backend:** Java 21 (JDK 21), patrón **MVC + DAO** (`HttpServer` embebido + API REST JSON).
- **Acceso a Datos:** **JDBC puro** (`DriverManager`, `PreparedStatement`, `ResultSet`) sobre vistas SQL (`vw_*`) y consultas parametrizadas.
- **Generación de PDFs:** **OpenPDF 2.0.3** (`GeneradorPDF.java`) para los **10 reportes gerenciales** y las **4 plantillas transaccionales** (Comprobante de Reserva, Comprobante de Pago, Factura de Pedido, Autorización de Tratamiento de Datos).
- **Frontend:** HTML5 + Bootstrap 5.3 + **Chart.js 4.4** (3 gráficos estadísticos en R-04, R-05 y R-06).
- **Seguridad:** Contraseñas almacenadas con hash **SHA-256** validado directamente contra las tablas `UsuarioSistema` (`administrador`, `encargado`, `operador`) y `PerfilCliente` (`cliente`).

---

## Estructura del Repositorio

```text
QuindioStay-BDI-Restaurantes/
├── sql/
│   ├── mysql/
│   │   ├── 01_DDL_QuindioStay.sql                 # Motor elegido: crea BD, 21 tablas, PK, FK, UNIQUE, CHECK
│   │   ├── 02_DML_QuindioStay.sql                 # Carga asimétrica (12 municipios, 20 restaurantes, 510 reservas...)
│   │   └── 03_Vistas_y_Reportes_QuindioStay.sql   # 15 vistas SQL (vw_*) y consultas de los 10 reportes
│   ├── sqlserver/
│   │   ├── 01_DDL_QuindioStay.sql
│   │   ├── 02_DML_QuindioStay.sql
│   │   └── 03_Vistas_y_Reportes_QuindioStay.sql   # Vistas CREATE OR ALTER VIEW ... GO (Clase 18)
│   ├── oracle/
│   │   ├── 01_DDL_QuindioStay.sql
│   │   ├── 02_DML_QuindioStay.sql
│   │   └── 03_Vistas_y_Reportes_QuindioStay.sql
│   └── sqlite/
│       └── 00_vistas_sqlite.sql                   # Vistas locales para arranque inmediato sin servidor externo
├── src/co/edu/uniquindio/quindiostay/
│   ├── Main.java                                  # Arranque del servidor HTTP e inicialización JDBC
│   ├── config/ConexionBD.java                     # Conexión JDBC multi-motor y hash SHA-256
│   ├── controller/ApiController.java              # Controlador REST + RBAC por rol + endpoints PDF
│   ├── dao/                                       # AuthDAO, CrudDAO (21 tablas), NegocioDAO, ReporteDAO
│   ├── pdf/GeneradorPDF.java                      # OpenPDF 2.0.3 (10 reportes + 4 plantillas)
│   └── util/JsonUtil.java                         # Serializador JSON nativo sin dependencias pesadas
├── public/index.html                              # Interfaz web Bootstrap 5 + Chart.js
├── lib/                                           # Dependencias JAR (OpenPDF, MySQL, SQL Server, SQLite)
├── data/quindiostay.db                            # Base de datos SQLite precargada para ejecución plug-and-play
├── docs/                                          # Diagramas MER/Arquitectura y capturas verificadas
├── pdf_exportados/                                # Salidas PDF de referencia de reportes y plantillas
├── config.properties                              # Selección de motor (sqlite | mysql | sqlserver)
├── pom.xml                                        # Descriptor Maven opcional (IntelliJ / VS Code)
├── COMPILAR.bat                                   # Compila el proyecto en Java 21
└── INICIAR_SISTEMA.bat                            # Compila (si falta ./out) y abre http://localhost:8080
```

---

## Ejecución Rápida (Windows + JDK 21)

1. Hacer doble clic en **`INICIAR_SISTEMA.bat`** (o ejecutar desde terminal `COMPILAR.bat` y luego `INICIAR_SISTEMA.bat`).
2. Abrir **`http://localhost:8080`** en el navegador.
3. Para conectar directamente a **MySQL 8.0** o **SQL Server 2022**:
   - Ejecutar los 3 scripts numerados (`01` -> `02` -> `03`) de la carpeta `sql/mysql/` o `sql/sqlserver/` en una base de datos vacía.
   - Cambiar `db.engine=mysql` o `db.engine=sqlserver` y sus credenciales en `config.properties`.

### Credenciales de Prueba
| Rol en BD | Usuario / Correo | Contraseña |
| :--- | :--- | :--- |
| `administrador` | `admin_quindio` | `Quindio2026*` |
| `encargado` | `encargado_cocora` | `Quindio2026*` |
| `operador` | `operador_salento` | `Quindio2026*` |
| `cliente` (`PerfilCliente`) | `valentina.arango1@correo.com` | `Cliente2026*` |
