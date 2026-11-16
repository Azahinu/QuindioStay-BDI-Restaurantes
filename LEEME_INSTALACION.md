# Instalación y ejecución - QuindíoStay (Dominio 4)

1. Orden para correr los scripts SQL en una base de datos vacía:
   - MySQL 8.0+ (motor principal):
     `sql/mysql/01_DDL_QuindioStay.sql` -> `sql/mysql/02_DML_QuindioStay.sql` -> `sql/mysql/03_Vistas_y_Reportes_QuindioStay.sql`
   - SQL Server 2022:
     `sql/sqlserver/01_DDL_QuindioStay.sql` -> `sql/sqlserver/02_DML_QuindioStay.sql` -> `sql/sqlserver/03_Vistas_y_Reportes_QuindioStay.sql`
   - Oracle 19c/21c:
     `sql/oracle/01_DDL_QuindioStay.sql` -> `sql/oracle/02_DML_QuindioStay.sql` -> `sql/oracle/03_Vistas_y_Reportes_QuindioStay.sql`

2. Ejecución del sistema en Java 21:
   - Por defecto `config.properties` conecta directamente a MySQL 8 (`db.engine=mysql`, base `QuindioStay`).
   - Dar doble clic en `INICIAR_SISTEMA.bat` (compila si falta la carpeta `out` y levanta el servidor en `http://localhost:8080`).
   - Si se desea probar sin servidor externo o con SQL Server, editar `db.engine=sqlite` o `sqlserver` en `config.properties`.
