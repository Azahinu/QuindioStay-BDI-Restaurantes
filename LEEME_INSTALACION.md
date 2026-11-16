# Instalación y ejecución - QuindíoStay (Dominio 4)

1. Orden para correr los scripts SQL en una base de datos vacía:
   - MySQL 8.0+ (motor principal):
     `sql/mysql/01_DDL_QuindioStay.sql` -> `sql/mysql/02_DML_QuindioStay.sql` -> `sql/mysql/03_Vistas_y_Reportes_QuindioStay.sql`
   - SQL Server 2022:
     `sql/sqlserver/01_DDL_QuindioStay.sql` -> `sql/sqlserver/02_DML_QuindioStay.sql` -> `sql/sqlserver/03_Vistas_y_Reportes_QuindioStay.sql`
   - Oracle 19c/21c:
     `sql/oracle/01_DDL_QuindioStay.sql` -> `sql/oracle/02_DML_QuindioStay.sql` -> `sql/oracle/03_Vistas_y_Reportes_QuindioStay.sql`

2. Ejecución del sistema en Java 21:
   - Dar doble clic en `INICIAR_SISTEMA.bat` (compila si falta la carpeta `out` y levanta el servidor local).
   - Por defecto usa `db.engine=sqlite` con `data/quindiostay.db` y abre en `http://localhost:8080`.
   - Para conectar a MySQL o SQL Server, editar `config.properties` (`db.engine=mysql` o `sqlserver`).
