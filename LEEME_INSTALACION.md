# Guía Rápida de Instalación y Ejecución — QuindíoStay (Dominio 4: Restaurantes)

1. **Orden de ejecución de scripts SQL en una base vacía:**
   - **MySQL 8.0+ (motor principal):**  
     `sql/mysql/01_DDL_QuindioStay.sql` -> `sql/mysql/02_DML_QuindioStay.sql` -> `sql/mysql/03_Vistas_y_Reportes_QuindioStay.sql`
   - **Microsoft SQL Server 2022:**  
     `sql/sqlserver/01_DDL_QuindioStay.sql` -> `sql/sqlserver/02_DML_QuindioStay.sql` -> `sql/sqlserver/03_Vistas_y_Reportes_QuindioStay.sql`
   - **Oracle Database 19c/21c:**  
     `sql/oracle/01_DDL_QuindioStay.sql` -> `sql/oracle/02_DML_QuindioStay.sql` -> `sql/oracle/03_Vistas_y_Reportes_QuindioStay.sql`

2. **Ejecución de la aplicación Java 21:**
   - Ejecute **`INICIAR_SISTEMA.bat`** (detecta automáticamente `java`/`javac` o `JAVA_HOME`).
   - Por defecto usa `db.engine=sqlite` con `data/quindiostay.db` para abrir al instante en **`http://localhost:8080`**.
   - Para apuntar a MySQL o SQL Server, edite `config.properties` (`db.engine=mysql` o `sqlserver`).
