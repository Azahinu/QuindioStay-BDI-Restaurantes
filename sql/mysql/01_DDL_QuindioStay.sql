-- ============================================================================
-- UNIVERSIDAD DEL QUINDIO - INGENIERIA DE SISTEMAS Y COMPUTACION
-- BASES DE DATOS I (12331 - GRUPO 02D) - DOCENTE: CAROLINA LONDONO IDARRAGA
-- PROYECTO FINAL: QUINDIOSTAY (DOMINIO 4: RESTAURANTES Y GASTRONOMIA)
-- ESTUDIANTE: ANDERSON SNEIDER MENESES ZAMBRANO
-- ARCHIVO 01: SCRIPT DDL DE CREACION DE TABLAS Y RESTRICCIONES (MYSQL 8.0+)
-- ============================================================================
-- Nota de Clase 13:
-- 1. Se crea o selecciona la base de datos QuindioStay con soporte UTF-8 completo.
-- 2. Se eliminan primero las tablas hijas (Nivel 5 a Nivel 1) por si existen.
-- 3. Se crean primero los padres (Nivel 1) y luego las hijas (Nivel 2 a 5).
-- ============================================================================

CREATE DATABASE IF NOT EXISTS QuindioStay
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
USE QuindioStay;

DROP TABLE IF EXISTS DetallePedido ;
DROP TABLE IF EXISTS Pedido ;
DROP TABLE IF EXISTS Notificacion ;
DROP TABLE IF EXISTS Pago ;
DROP TABLE IF EXISTS ReservaMesa ;
DROP TABLE IF EXISTS Reserva ;
DROP TABLE IF EXISTS PlatoEtiqueta ;
DROP TABLE IF EXISTS Domiciliario ;
DROP TABLE IF EXISTS Experiencia ;
DROP TABLE IF EXISTS Plato ;
DROP TABLE IF EXISTS Mesa ;
DROP TABLE IF EXISTS Plan ;
DROP TABLE IF EXISTS UsuarioSistema ;
DROP TABLE IF EXISTS PerfilCliente ;
DROP TABLE IF EXISTS Prestador ;
DROP TABLE IF EXISTS Etiqueta ;
DROP TABLE IF EXISTS CategoriaPlato ;
DROP TABLE IF EXISTS Cliente ;
DROP TABLE IF EXISTS Rol ;
DROP TABLE IF EXISTS TipoPrestador ;
DROP TABLE IF EXISTS Municipio ;

-- ============================================================================
-- NIVEL 1: TABLAS PADRE (NO DEPENDEN DE NINGUNA OTRA TABLA)
-- ============================================================================

-- 1. Tabla Municipio: guarda los 12 municipios del departamento del Quindio.
--    codigo_dane es CHAR(5) porque no se hacen calculos con el y mide siempre 5.
CREATE TABLE Municipio (
    codigo_dane CHAR(5)      CONSTRAINT pk_municipio PRIMARY KEY,
    nombre      VARCHAR(80) NOT NULL,
    CONSTRAINT uq_municipio_nombre UNIQUE (nombre)
);

-- 2. Tabla TipoPrestador: catalogo de categorias de prestadores turisticos.
CREATE TABLE TipoPrestador (
    id_tipo_prestador INT    CONSTRAINT pk_tipoprestador PRIMARY KEY,
    nombre_tipo       VARCHAR(60) NOT NULL,
    CONSTRAINT uq_tipoprestador_nombre UNIQUE (nombre_tipo)
);

-- 3. Tabla Rol: roles permitidos dentro de la plataforma QuindioStay.
CREATE TABLE Rol (
    id_rol     INT    CONSTRAINT pk_rol PRIMARY KEY,
    nombre_rol VARCHAR(50) NOT NULL,
    CONSTRAINT uq_rol_nombre UNIQUE (nombre_rol),
    CONSTRAINT ck_rol_nombre CHECK (nombre_rol IN ('administrador', 'encargado', 'operador', 'cliente'))
);

-- 4. Tabla Cliente: turistas y comensales registrados en la plataforma.
--    Clave alterna compuesta en (tipo_documento, numero_documento) y en correo.
CREATE TABLE Cliente (
    id_cliente       INT    CONSTRAINT pk_cliente PRIMARY KEY,
    tipo_documento   VARCHAR(10)  NOT NULL,
    numero_documento VARCHAR(25)  NOT NULL,
    nombres          VARCHAR(80)  NOT NULL,
    apellidos        VARCHAR(80)  NOT NULL,
    correo           VARCHAR(100) NOT NULL,
    telefono         VARCHAR(20)  NOT NULL,
    fecha_nacimiento DATE          NOT NULL,
    ciudad_origen    VARCHAR(80),
    CONSTRAINT uq_cliente_documento UNIQUE (tipo_documento, numero_documento),
    CONSTRAINT uq_cliente_correo    UNIQUE (correo),
    CONSTRAINT ck_cliente_tipodoc   CHECK (tipo_documento IN ('CC', 'CE', 'PA', 'TI', 'NIT'))
);

-- 5. Tabla CategoriaPlato: secciones de la carta gastronomica.
CREATE TABLE CategoriaPlato (
    id_categoria     INT     CONSTRAINT pk_categoriaplato PRIMARY KEY,
    nombre_categoria VARCHAR(60)  NOT NULL,
    descripcion      VARCHAR(200),
    CONSTRAINT uq_categoriaplato_nombre UNIQUE (nombre_categoria)
);

-- 6. Tabla Etiqueta: etiquetas dietarias y culinarias (vegano, sin gluten, etc.).
CREATE TABLE Etiqueta (
    id_etiqueta     INT    CONSTRAINT pk_etiqueta PRIMARY KEY,
    nombre_etiqueta VARCHAR(50) NOT NULL,
    CONSTRAINT uq_etiqueta_nombre UNIQUE (nombre_etiqueta)
);

-- ============================================================================
-- NIVEL 2: TABLAS QUE DEPENDEN UNICAMENTE DEL NIVEL 1
-- ============================================================================

-- 7. Tabla Prestador: restaurantes y operadores registrados en el Quindio.
--    Sus foraneas hacia Municipio y TipoPrestador usan RESTRICT (sin ON DELETE).
CREATE TABLE Prestador (
    id_prestador               INT     CONSTRAINT pk_prestador PRIMARY KEY,
    codigo_dane                CHAR(5)       NOT NULL,
    id_tipo_prestador          INT     NOT NULL,
    nit_documento              VARCHAR(20)  NOT NULL,
    nombre_comercial           VARCHAR(120) NOT NULL,
    rnt                        VARCHAR(30),
    direccion                  VARCHAR(150) NOT NULL,
    telefono                   VARCHAR(20)  NOT NULL,
    correo                     VARCHAR(100) NOT NULL,
    estado                     VARCHAR(15)  DEFAULT 'activo' NOT NULL,
    distancia_max_domicilio_km DECIMAL(5,2)   DEFAULT 0 NOT NULL,
    CONSTRAINT uq_prestador_nit       UNIQUE (nit_documento),
    CONSTRAINT ck_prestador_estado    CHECK (estado IN ('activo', 'inactivo', 'suspendido')),
    CONSTRAINT ck_prestador_distancia CHECK (distancia_max_domicilio_km >= 0),
    CONSTRAINT fk_prestador_municipio FOREIGN KEY (codigo_dane) REFERENCES Municipio (codigo_dane),
    CONSTRAINT fk_prestador_tipo      FOREIGN KEY (id_tipo_prestador) REFERENCES TipoPrestador (id_tipo_prestador)
);

-- 8. Tabla PerfilCliente: entidad debil 1:1 con Cliente para cuenta y preferencias.
--    Usa ON DELETE CASCADE porque el perfil no tiene sentido sin el cliente.
CREATE TABLE PerfilCliente (
    id_cliente                INT    CONSTRAINT pk_perfilcliente PRIMARY KEY,
    contrasena_hash           VARCHAR(255) NOT NULL,
    idioma                    VARCHAR(5)   DEFAULT 'es' NOT NULL,
    canal_preferido           VARCHAR(15)  DEFAULT 'correo' NOT NULL,
    anticipacion_recordatorio INT     DEFAULT 24 NOT NULL,
    acepta_promociones        INT     DEFAULT 1 NOT NULL,
    CONSTRAINT ck_perfil_idioma      CHECK (idioma IN ('es', 'en', 'fr')),
    CONSTRAINT ck_perfil_canal       CHECK (canal_preferido IN ('correo', 'SMS', 'WhatsApp')),
    CONSTRAINT ck_perfil_anticip     CHECK (anticipacion_recordatorio > 0),
    CONSTRAINT ck_perfil_promociones CHECK (acepta_promociones IN (0, 1)),
    CONSTRAINT fk_perfil_cliente     FOREIGN KEY (id_cliente) REFERENCES Cliente (id_cliente) ON DELETE CASCADE
);

-- ============================================================================
-- NIVEL 3: TABLAS QUE DEPENDEN DE PRESTADOR Y ROL
-- ============================================================================

-- 9. Tabla UsuarioSistema: cuentas de administradores, encargados y operadores.
--    id_prestador admite NULL para el administrador general (ON DELETE SET NULL).
CREATE TABLE UsuarioSistema (
    id_usuario      INT     CONSTRAINT pk_usuariosistema PRIMARY KEY,
    id_rol          INT     NOT NULL,
    id_prestador    INT,
    nombre_usuario  VARCHAR(50)  NOT NULL,
    contrasena_hash VARCHAR(255) NOT NULL,
    estado          VARCHAR(15)  DEFAULT 'activo' NOT NULL,
    CONSTRAINT uq_usuario_login     UNIQUE (nombre_usuario),
    CONSTRAINT ck_usuario_estado    CHECK (estado IN ('activo', 'inactivo')),
    CONSTRAINT fk_usuario_rol       FOREIGN KEY (id_rol) REFERENCES Rol (id_rol),
    CONSTRAINT fk_usuario_prestador FOREIGN KEY (id_prestador) REFERENCES Prestador (id_prestador) ON DELETE SET NULL
);

-- 10. Tabla Plan: planes gastronomicos y de degustacion ofrecidos por cada prestador.
CREATE TABLE Plan (
    id_plan               INT     CONSTRAINT pk_plan PRIMARY KEY,
    id_prestador          INT     NOT NULL,
    nombre                VARCHAR(120) NOT NULL,
    descripcion           VARCHAR(400) NOT NULL,
    tipo                  VARCHAR(40)  NOT NULL,
    precio                DECIMAL(10,2)  NOT NULL,
    fecha_inicio_vigencia DATE          NOT NULL,
    fecha_fin_vigencia    DATE          NOT NULL,
    estado                VARCHAR(15)  DEFAULT 'activo' NOT NULL,
    CONSTRAINT uq_plan_prestador_nombre UNIQUE (id_prestador, nombre),
    CONSTRAINT ck_plan_tipo             CHECK (tipo IN ('gastronomico', 'degustacion', 'evento')),
    CONSTRAINT ck_plan_precio           CHECK (precio > 0),
    CONSTRAINT ck_plan_fechas           CHECK (fecha_fin_vigencia >= fecha_inicio_vigencia),
    CONSTRAINT ck_plan_estado           CHECK (estado IN ('activo', 'inactivo', 'vencido')),
    CONSTRAINT fk_plan_prestador        FOREIGN KEY (id_prestador) REFERENCES Prestador (id_prestador)
);

-- 11. Tabla Mesa: inventario de mesas fisicas de cada restaurante.
--     Ninguna mesa individual supera 12 sillas; grupos de 14 juntan mesas en ReservaMesa.
CREATE TABLE Mesa (
    id_mesa      INT    CONSTRAINT pk_mesa PRIMARY KEY,
    id_prestador INT    NOT NULL,
    numero_mesa  INT    NOT NULL,
    capacidad    INT    NOT NULL,
    zona         VARCHAR(20) NOT NULL,
    estado       VARCHAR(20) DEFAULT 'disponible' NOT NULL,
    CONSTRAINT uq_mesa_prestador_num UNIQUE (id_prestador, numero_mesa),
    CONSTRAINT ck_mesa_capacidad     CHECK (capacidad BETWEEN 1 AND 12),
    CONSTRAINT ck_mesa_zona          CHECK (zona IN ('salon', 'terraza', 'mirador', 'barra')),
    CONSTRAINT ck_mesa_estado        CHECK (estado IN ('disponible', 'ocupada', 'reservada', 'fuera_de_servicio')),
    CONSTRAINT fk_mesa_prestador     FOREIGN KEY (id_prestador) REFERENCES Prestador (id_prestador)
);

-- 12. Tabla Plato: platos registrados en la carta de cada restaurante.
CREATE TABLE Plato (
    id_plato      INT     CONSTRAINT pk_plato PRIMARY KEY,
    id_prestador  INT     NOT NULL,
    id_categoria  INT     NOT NULL,
    nombre_plato  VARCHAR(120) NOT NULL,
    descripcion   VARCHAR(400) NOT NULL,
    precio_actual DECIMAL(10,2)  NOT NULL,
    estado        VARCHAR(15)  DEFAULT 'disponible' NOT NULL,
    CONSTRAINT uq_plato_prestador_nombre UNIQUE (id_prestador, nombre_plato),
    CONSTRAINT ck_plato_precio           CHECK (precio_actual > 0),
    CONSTRAINT ck_plato_estado           CHECK (estado IN ('disponible', 'agotado', 'inactivo')),
    CONSTRAINT fk_plato_prestador        FOREIGN KEY (id_prestador) REFERENCES Prestador (id_prestador),
    CONSTRAINT fk_plato_categoria        FOREIGN KEY (id_categoria) REFERENCES CategoriaPlato (id_categoria)
);

-- 13. Tabla Experiencia: catas de cafe, talleres de cocina y experiencias con cupo.
CREATE TABLE Experiencia (
    id_experiencia     INT     CONSTRAINT pk_experiencia PRIMARY KEY,
    id_prestador       INT     NOT NULL,
    nombre_experiencia VARCHAR(120) NOT NULL,
    descripcion        VARCHAR(400) NOT NULL,
    cupo_maximo        INT     NOT NULL,
    precio_persona     DECIMAL(10,2)  NOT NULL,
    duracion_horas     DECIMAL(4,2)   NOT NULL,
    estado             VARCHAR(15)  DEFAULT 'activa' NOT NULL,
    CONSTRAINT ck_experiencia_cupo     CHECK (cupo_maximo > 0),
    CONSTRAINT ck_experiencia_precio   CHECK (precio_persona > 0),
    CONSTRAINT ck_experiencia_duracion CHECK (duracion_horas > 0),
    CONSTRAINT ck_experiencia_estado   CHECK (estado IN ('activa', 'inactiva')),
    CONSTRAINT fk_experiencia_prest    FOREIGN KEY (id_prestador) REFERENCES Prestador (id_prestador)
);

-- 14. Tabla Domiciliario: repartidores vinculados a los restaurantes.
CREATE TABLE Domiciliario (
    id_domiciliario INT    CONSTRAINT pk_domiciliario PRIMARY KEY,
    id_prestador    INT    NOT NULL,
    documento       VARCHAR(20) NOT NULL,
    nombres         VARCHAR(80) NOT NULL,
    apellidos       VARCHAR(80) NOT NULL,
    telefono        VARCHAR(20) NOT NULL,
    tipo_vehiculo   VARCHAR(20) NOT NULL,
    estado          VARCHAR(15) DEFAULT 'disponible' NOT NULL,
    CONSTRAINT uq_domiciliario_doc      UNIQUE (documento),
    CONSTRAINT ck_domiciliario_vehiculo CHECK (tipo_vehiculo IN ('motocicleta', 'bicicleta', 'automovil')),
    CONSTRAINT ck_domiciliario_estado   CHECK (estado IN ('disponible', 'en_ruta', 'inactivo')),
    CONSTRAINT fk_domiciliario_prest    FOREIGN KEY (id_prestador) REFERENCES Prestador (id_prestador)
);

-- ============================================================================
-- NIVEL 4: TABLA PUENTE DE PLATOS Y TABLA DE RESERVAS
-- ============================================================================

-- 15. Tabla PlatoEtiqueta: rompe la relacion muchos a muchos entre Plato y Etiqueta.
CREATE TABLE PlatoEtiqueta (
    id_plato    INT NOT NULL,
    id_etiqueta INT NOT NULL,
    CONSTRAINT pk_platoetiqueta   PRIMARY KEY (id_plato, id_etiqueta),
    CONSTRAINT fk_platoetiq_plato FOREIGN KEY (id_plato) REFERENCES Plato (id_plato) ON DELETE CASCADE,
    CONSTRAINT fk_platoetiq_etiq  FOREIGN KEY (id_etiqueta) REFERENCES Etiqueta (id_etiqueta)
);

-- 16. Tabla Reserva: reservas de mesas, planes gastronomicos o experiencias.
--     Incluye id_experiencia* atendiendo la retroalimentacion de la Entrega 1.
CREATE TABLE Reserva (
    id_reserva        INT   CONSTRAINT pk_reserva PRIMARY KEY,
    id_cliente        INT   NOT NULL,
    id_plan           INT,
    id_experiencia    INT,
    fecha_creacion    DATE         DEFAULT (CURRENT_DATE) NOT NULL,
    fecha_servicio    DATE         NOT NULL,
    franja_horaria    VARCHAR(20) NOT NULL,
    numero_comensales INT    NOT NULL,
    valor_total       DECIMAL(12,2) NOT NULL,
    canal             VARCHAR(20) NOT NULL,
    estado            VARCHAR(15) DEFAULT 'pendiente' NOT NULL,
    CONSTRAINT ck_reserva_franja     CHECK (franja_horaria IN ('desayuno', 'almuerzo', 'cena')),
    CONSTRAINT ck_reserva_comensales CHECK (numero_comensales > 0),
    CONSTRAINT ck_reserva_valor      CHECK (valor_total >= 0),
    CONSTRAINT ck_reserva_canal      CHECK (canal IN ('web', 'telefono', 'presencial', 'WhatsApp')),
    CONSTRAINT ck_reserva_estado     CHECK (estado IN ('pendiente', 'confirmada', 'en_curso', 'completada', 'cancelada')),
    CONSTRAINT fk_reserva_cliente    FOREIGN KEY (id_cliente) REFERENCES Cliente (id_cliente),
    CONSTRAINT fk_reserva_plan       FOREIGN KEY (id_plan) REFERENCES Plan (id_plan),
    CONSTRAINT fk_reserva_exper      FOREIGN KEY (id_experiencia) REFERENCES Experiencia (id_experiencia)
);

-- ============================================================================
-- NIVEL 5: TABLAS HIJAS DE RESERVA Y PEDIDOS GASTRONOMICOS
-- ============================================================================

-- 17. Tabla ReservaMesa: tabla puente que permite acoplar varias mesas para
--     grupos grandes (ej. 14 personas juntando mesas de 8 y 6 puestos).
CREATE TABLE ReservaMesa (
    id_reserva INT NOT NULL,
    id_mesa    INT  NOT NULL,
    CONSTRAINT pk_reservamesa     PRIMARY KEY (id_reserva, id_mesa),
    CONSTRAINT fk_resmesa_reserva FOREIGN KEY (id_reserva) REFERENCES Reserva (id_reserva) ON DELETE CASCADE,
    CONSTRAINT fk_resmesa_mesa    FOREIGN KEY (id_mesa) REFERENCES Mesa (id_mesa)
);

-- 18. Tabla Pago: abonos y pagos totales asociados a una reserva (sin id_cliente para cumplir 3FN).
CREATE TABLE Pago (
    id_pago                INT   CONSTRAINT pk_pago PRIMARY KEY,
    id_reserva             INT   NOT NULL,
    fecha_pago             DATE         DEFAULT (CURRENT_DATE) NOT NULL,
    monto                  DECIMAL(12,2) NOT NULL,
    metodo                 VARCHAR(25) NOT NULL,
    estado                 VARCHAR(20) DEFAULT 'aprobado' NOT NULL,
    referencia_transaccion VARCHAR(60),
    CONSTRAINT uq_pago_referencia UNIQUE (referencia_transaccion),
    CONSTRAINT ck_pago_monto      CHECK (monto > 0),
    CONSTRAINT ck_pago_metodo     CHECK (metodo IN ('efectivo', 'tarjeta', 'transferencia', 'PSE')),
    CONSTRAINT ck_pago_estado     CHECK (estado IN ('aprobado', 'pendiente', 'rechazado', 'reembolsado_80', 'retenido')),
    CONSTRAINT fk_pago_reserva    FOREIGN KEY (id_reserva) REFERENCES Reserva (id_reserva)
);

-- 19. Tabla Notificacion: historial de avisos y promociones enviados al cliente.
--     fk_notif_cliente usa RESTRICT (sin ON DELETE CASCADE) para conservar el historial.
CREATE TABLE Notificacion (
    id_notificacion  INT    CONSTRAINT pk_notificacion PRIMARY KEY,
    id_cliente       INT    NOT NULL,
    id_reserva       INT,
    tipo             VARCHAR(25)  NOT NULL,
    canal            VARCHAR(15)  NOT NULL,
    mensaje          VARCHAR(400) NOT NULL,
    fecha_programada DATE          NOT NULL,
    fecha_real       DATE,
    estado           VARCHAR(15)  DEFAULT 'pendiente' NOT NULL,
    CONSTRAINT ck_notif_tipo    CHECK (tipo IN ('confirmacion', 'recordatorio', 'cancelacion', 'promocion')),
    CONSTRAINT ck_notif_canal   CHECK (canal IN ('correo', 'SMS', 'WhatsApp')),
    CONSTRAINT ck_notif_estado  CHECK (estado IN ('pendiente', 'enviada', 'fallida')),
    CONSTRAINT ck_notif_fechas  CHECK (fecha_real IS NULL OR fecha_real >= fecha_programada),
    CONSTRAINT fk_notif_cliente FOREIGN KEY (id_cliente) REFERENCES Cliente (id_cliente),
    CONSTRAINT fk_notif_reserva FOREIGN KEY (id_reserva) REFERENCES Reserva (id_reserva) ON DELETE SET NULL
);

-- 20. Tabla Pedido: cabecera de comandas en mesa, para llevar o a domicilio.
--     Incluye CHECK compuesto para validar que un domicilio tenga direccion y un pedido en mesa tenga mesa.
CREATE TABLE Pedido (
    id_pedido         INT    CONSTRAINT pk_pedido PRIMARY KEY,
    id_prestador      INT     NOT NULL,
    id_cliente        INT,
    id_reserva        INT,
    id_mesa           INT,
    id_domiciliario   INT,
    fecha_hora        DATE          DEFAULT (CURRENT_DATE) NOT NULL,
    tipo_pedido       VARCHAR(15)  NOT NULL,
    direccion_entrega VARCHAR(180),
    costo_envio       DECIMAL(10,2)  DEFAULT 0 NOT NULL,
    total_pedido      DECIMAL(12,2)  NOT NULL,
    estado_pedido     VARCHAR(20)  DEFAULT 'recibido' NOT NULL,
    CONSTRAINT ck_pedido_tipo      CHECK (tipo_pedido IN ('en_mesa', 'para_llevar', 'domicilio')),
    CONSTRAINT ck_pedido_envio     CHECK (costo_envio >= 0),
    CONSTRAINT ck_pedido_total     CHECK (total_pedido > 0),
    CONSTRAINT ck_pedido_estado    CHECK (estado_pedido IN ('recibido', 'en_preparacion', 'en_camino', 'entregado', 'cancelado')),
    CONSTRAINT ck_pedido_modalidad CHECK (
        (tipo_pedido = 'domicilio' AND direccion_entrega IS NOT NULL) OR
        (tipo_pedido = 'en_mesa' AND id_mesa IS NOT NULL) OR
        (tipo_pedido = 'para_llevar')
    ),
    CONSTRAINT fk_pedido_prestador    FOREIGN KEY (id_prestador) REFERENCES Prestador (id_prestador),
    CONSTRAINT fk_pedido_cliente      FOREIGN KEY (id_cliente) REFERENCES Cliente (id_cliente) ON DELETE SET NULL,
    CONSTRAINT fk_pedido_reserva      FOREIGN KEY (id_reserva) REFERENCES Reserva (id_reserva) ON DELETE SET NULL,
    CONSTRAINT fk_pedido_mesa         FOREIGN KEY (id_mesa) REFERENCES Mesa (id_mesa),
    CONSTRAINT fk_pedido_domiciliario FOREIGN KEY (id_domiciliario) REFERENCES Domiciliario (id_domiciliario) ON DELETE SET NULL
);

-- 21. Tabla DetallePedido: entidad debil de Pedido con clave compuesta (id_pedido*, num_linea).
--     Guarda precio_unitario_historico para congelar el valor cobrado ese dia.
CREATE TABLE DetallePedido (
    id_pedido                 INT   NOT NULL,
    num_linea                 INT    NOT NULL,
    id_plato                  INT    NOT NULL,
    cantidad                  INT    NOT NULL,
    precio_unitario_historico DECIMAL(10,2) NOT NULL,
    subtotal                  DECIMAL(12,2) NOT NULL,
    CONSTRAINT pk_detallepedido     PRIMARY KEY (id_pedido, num_linea),
    CONSTRAINT ck_detalle_cantidad  CHECK (cantidad > 0),
    CONSTRAINT ck_detalle_precio    CHECK (precio_unitario_historico > 0),
    CONSTRAINT ck_detalle_subtotal  CHECK (subtotal = cantidad * precio_unitario_historico),
    CONSTRAINT fk_detalle_pedido    FOREIGN KEY (id_pedido) REFERENCES Pedido (id_pedido) ON DELETE CASCADE,
    CONSTRAINT fk_detalle_plato     FOREIGN KEY (id_plato) REFERENCES Plato (id_plato)
);
