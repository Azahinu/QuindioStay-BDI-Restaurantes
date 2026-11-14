-- ============================================================================
-- UNIVERSIDAD DEL QUINDIO - INGENIERIA DE SISTEMAS Y COMPUTACION
-- BASES DE DATOS I (12331 - GRUPO 02D) - DOCENTE: CAROLINA LONDONO IDARRAGA
-- PROYECTO FINAL: QUINDIOSTAY (DOMINIO 4: RESTAURANTES Y GASTRONOMIA)
-- ESTUDIANTE: ANDERSON SNEIDER MENESES ZAMBRANO
-- ARCHIVO 01: SCRIPT DDL COMPLETO PARA MICROSOFT SQL SERVER (2022 / EXPRESS)
-- ============================================================================
IF DB_ID('QuindioStay') IS NULL
BEGIN
    CREATE DATABASE QuindioStay;
END;
GO
USE QuindioStay;
GO

DROP TABLE IF EXISTS DetallePedido;
DROP TABLE IF EXISTS Pedido;
DROP TABLE IF EXISTS Notificacion;
DROP TABLE IF EXISTS Pago;
DROP TABLE IF EXISTS ReservaMesa;
DROP TABLE IF EXISTS Reserva;
DROP TABLE IF EXISTS PlatoEtiqueta;
DROP TABLE IF EXISTS Domiciliario;
DROP TABLE IF EXISTS Experiencia;
DROP TABLE IF EXISTS Plato;
DROP TABLE IF EXISTS Mesa;
DROP TABLE IF EXISTS [Plan];
DROP TABLE IF EXISTS UsuarioSistema;
DROP TABLE IF EXISTS PerfilCliente;
DROP TABLE IF EXISTS Prestador;
DROP TABLE IF EXISTS Etiqueta;
DROP TABLE IF EXISTS CategoriaPlato;
DROP TABLE IF EXISTS Cliente;
DROP TABLE IF EXISTS Rol;
DROP TABLE IF EXISTS TipoPrestador;
DROP TABLE IF EXISTS Municipio;
GO

CREATE TABLE Municipio (
    codigo_dane CHAR(5)      CONSTRAINT pk_municipio PRIMARY KEY,
    nombre      VARCHAR(80)  NOT NULL,
    CONSTRAINT uq_municipio_nombre UNIQUE (nombre)
);

CREATE TABLE TipoPrestador (
    id_tipo_prestador INT         CONSTRAINT pk_tipoprestador PRIMARY KEY,
    nombre_tipo       VARCHAR(60) NOT NULL,
    CONSTRAINT uq_tipoprestador_nombre UNIQUE (nombre_tipo)
);

CREATE TABLE Rol (
    id_rol     INT         CONSTRAINT pk_rol PRIMARY KEY,
    nombre_rol VARCHAR(50) NOT NULL,
    CONSTRAINT uq_rol_nombre UNIQUE (nombre_rol),
    CONSTRAINT ck_rol_nombre CHECK (nombre_rol IN ('administrador', 'encargado', 'operador', 'cliente'))
);

CREATE TABLE Cliente (
    id_cliente       INT          CONSTRAINT pk_cliente PRIMARY KEY,
    tipo_documento   VARCHAR(10)  NOT NULL,
    numero_documento VARCHAR(25)  NOT NULL,
    nombres          VARCHAR(80)  NOT NULL,
    apellidos        VARCHAR(80)  NOT NULL,
    correo           VARCHAR(100) NOT NULL,
    telefono         VARCHAR(20)  NOT NULL,
    fecha_nacimiento DATE         NOT NULL,
    ciudad_origen    VARCHAR(80)  NULL,
    CONSTRAINT uq_cliente_documento UNIQUE (tipo_documento, numero_documento),
    CONSTRAINT uq_cliente_correo    UNIQUE (correo),
    CONSTRAINT ck_cliente_tipodoc   CHECK (tipo_documento IN ('CC', 'CE', 'PA', 'TI', 'NIT'))
);

CREATE TABLE CategoriaPlato (
    id_categoria     INT          CONSTRAINT pk_categoriaplato PRIMARY KEY,
    nombre_categoria VARCHAR(60)  NOT NULL,
    descripcion      VARCHAR(200) NULL,
    CONSTRAINT uq_categoriaplato_nombre UNIQUE (nombre_categoria)
);

CREATE TABLE Etiqueta (
    id_etiqueta     INT         CONSTRAINT pk_etiqueta PRIMARY KEY,
    nombre_etiqueta VARCHAR(50) NOT NULL,
    CONSTRAINT uq_etiqueta_nombre UNIQUE (nombre_etiqueta)
);
GO

CREATE TABLE Prestador (
    id_prestador               INT           CONSTRAINT pk_prestador PRIMARY KEY,
    codigo_dane                CHAR(5)       NOT NULL,
    id_tipo_prestador          INT           NOT NULL,
    nit_documento              VARCHAR(20)   NOT NULL,
    nombre_comercial           VARCHAR(120)  NOT NULL,
    rnt                        VARCHAR(30)   NULL,
    direccion                  VARCHAR(150)  NOT NULL,
    telefono                   VARCHAR(20)   NOT NULL,
    correo                     VARCHAR(100)  NOT NULL,
    estado                     VARCHAR(15)   NOT NULL CONSTRAINT df_prestador_estado DEFAULT 'activo',
    distancia_max_domicilio_km DECIMAL(5,2)  NOT NULL CONSTRAINT df_prestador_dist DEFAULT 0,
    CONSTRAINT uq_prestador_nit       UNIQUE (nit_documento),
    CONSTRAINT ck_prestador_estado    CHECK (estado IN ('activo', 'inactivo', 'suspendido')),
    CONSTRAINT ck_prestador_distancia CHECK (distancia_max_domicilio_km >= 0),
    CONSTRAINT fk_prestador_municipio FOREIGN KEY (codigo_dane) REFERENCES Municipio (codigo_dane) ON DELETE NO ACTION,
    CONSTRAINT fk_prestador_tipo      FOREIGN KEY (id_tipo_prestador) REFERENCES TipoPrestador (id_tipo_prestador) ON DELETE NO ACTION
);

CREATE TABLE PerfilCliente (
    id_cliente                INT          CONSTRAINT pk_perfilcliente PRIMARY KEY,
    contrasena_hash           VARCHAR(255) NOT NULL,
    idioma                    VARCHAR(5)   NOT NULL CONSTRAINT df_perfil_idioma DEFAULT 'es',
    canal_preferido           VARCHAR(15)  NOT NULL CONSTRAINT df_perfil_canal DEFAULT 'correo',
    anticipacion_recordatorio INT          NOT NULL CONSTRAINT df_perfil_ant DEFAULT 24,
    acepta_promociones        INT          NOT NULL CONSTRAINT df_perfil_promo DEFAULT 1,
    CONSTRAINT ck_perfil_idioma      CHECK (idioma IN ('es', 'en', 'fr')),
    CONSTRAINT ck_perfil_canal       CHECK (canal_preferido IN ('correo', 'SMS', 'WhatsApp')),
    CONSTRAINT ck_perfil_anticip     CHECK (anticipacion_recordatorio > 0),
    CONSTRAINT ck_perfil_promociones CHECK (acepta_promociones IN (0, 1)),
    CONSTRAINT fk_perfil_cliente     FOREIGN KEY (id_cliente) REFERENCES Cliente (id_cliente) ON DELETE CASCADE
);
GO

CREATE TABLE UsuarioSistema (
    id_usuario      INT          CONSTRAINT pk_usuariosistema PRIMARY KEY,
    id_rol          INT          NOT NULL,
    id_prestador    INT          NULL,
    nombre_usuario  VARCHAR(50)  NOT NULL,
    contrasena_hash VARCHAR(255) NOT NULL,
    estado          VARCHAR(15)  NOT NULL CONSTRAINT df_usr_estado DEFAULT 'activo',
    CONSTRAINT uq_usuario_login     UNIQUE (nombre_usuario),
    CONSTRAINT ck_usuario_estado    CHECK (estado IN ('activo', 'inactivo')),
    CONSTRAINT fk_usuario_rol       FOREIGN KEY (id_rol) REFERENCES Rol (id_rol) ON DELETE NO ACTION,
    CONSTRAINT fk_usuario_prestador FOREIGN KEY (id_prestador) REFERENCES Prestador (id_prestador) ON DELETE SET NULL
);

CREATE TABLE [Plan] (
    id_plan               INT           CONSTRAINT pk_plan PRIMARY KEY,
    id_prestador          INT           NOT NULL,
    nombre                VARCHAR(120)  NOT NULL,
    descripcion           VARCHAR(400)  NOT NULL,
    tipo                  VARCHAR(40)   NOT NULL,
    precio                DECIMAL(10,2) NOT NULL,
    fecha_inicio_vigencia DATE          NOT NULL,
    fecha_fin_vigencia    DATE          NOT NULL,
    estado                VARCHAR(15)   NOT NULL CONSTRAINT df_plan_estado DEFAULT 'activo',
    CONSTRAINT uq_plan_prestador_nombre UNIQUE (id_prestador, nombre),
    CONSTRAINT ck_plan_tipo             CHECK (tipo IN ('gastronomico', 'degustacion', 'evento')),
    CONSTRAINT ck_plan_precio           CHECK (precio > 0),
    CONSTRAINT ck_plan_fechas           CHECK (fecha_fin_vigencia >= fecha_inicio_vigencia),
    CONSTRAINT ck_plan_estado           CHECK (estado IN ('activo', 'inactivo', 'vencido')),
    CONSTRAINT fk_plan_prestador        FOREIGN KEY (id_prestador) REFERENCES Prestador (id_prestador) ON DELETE NO ACTION
);

CREATE TABLE Mesa (
    id_mesa      INT         CONSTRAINT pk_mesa PRIMARY KEY,
    id_prestador INT         NOT NULL,
    numero_mesa  INT         NOT NULL,
    capacidad    INT         NOT NULL,
    zona         VARCHAR(20) NOT NULL,
    estado       VARCHAR(20) NOT NULL CONSTRAINT df_mesa_estado DEFAULT 'disponible',
    CONSTRAINT uq_mesa_prestador_num UNIQUE (id_prestador, numero_mesa),
    CONSTRAINT ck_mesa_capacidad     CHECK (capacidad BETWEEN 1 AND 12),
    CONSTRAINT ck_mesa_zona          CHECK (zona IN ('salon', 'terraza', 'mirador', 'barra')),
    CONSTRAINT ck_mesa_estado        CHECK (estado IN ('disponible', 'ocupada', 'reservada', 'fuera_de_servicio')),
    CONSTRAINT fk_mesa_prestador     FOREIGN KEY (id_prestador) REFERENCES Prestador (id_prestador) ON DELETE NO ACTION
);

CREATE TABLE Plato (
    id_plato      INT           CONSTRAINT pk_plato PRIMARY KEY,
    id_prestador  INT           NOT NULL,
    id_categoria  INT           NOT NULL,
    nombre_plato  VARCHAR(120)  NOT NULL,
    descripcion   VARCHAR(400)  NOT NULL,
    precio_actual DECIMAL(10,2) NOT NULL,
    estado        VARCHAR(15)   NOT NULL CONSTRAINT df_plato_estado DEFAULT 'disponible',
    CONSTRAINT uq_plato_prestador_nombre UNIQUE (id_prestador, nombre_plato),
    CONSTRAINT ck_plato_precio           CHECK (precio_actual > 0),
    CONSTRAINT ck_plato_estado           CHECK (estado IN ('disponible', 'agotado', 'inactivo')),
    CONSTRAINT fk_plato_prestador        FOREIGN KEY (id_prestador) REFERENCES Prestador (id_prestador) ON DELETE NO ACTION,
    CONSTRAINT fk_plato_categoria        FOREIGN KEY (id_categoria) REFERENCES CategoriaPlato (id_categoria) ON DELETE NO ACTION
);

CREATE TABLE Experiencia (
    id_experiencia     INT           CONSTRAINT pk_experiencia PRIMARY KEY,
    id_prestador       INT           NOT NULL,
    nombre_experiencia VARCHAR(120)  NOT NULL,
    descripcion        VARCHAR(400)  NOT NULL,
    cupo_maximo        INT           NOT NULL,
    precio_persona     DECIMAL(10,2) NOT NULL,
    duracion_horas     DECIMAL(4,2)  NOT NULL,
    estado             VARCHAR(15)   NOT NULL CONSTRAINT df_exp_estado DEFAULT 'activa',
    CONSTRAINT ck_experiencia_cupo     CHECK (cupo_maximo > 0),
    CONSTRAINT ck_experiencia_precio   CHECK (precio_persona > 0),
    CONSTRAINT ck_experiencia_duracion CHECK (duracion_horas > 0),
    CONSTRAINT ck_experiencia_estado   CHECK (estado IN ('activa', 'inactiva')),
    CONSTRAINT fk_experiencia_prest    FOREIGN KEY (id_prestador) REFERENCES Prestador (id_prestador) ON DELETE NO ACTION
);

CREATE TABLE Domiciliario (
    id_domiciliario INT         CONSTRAINT pk_domiciliario PRIMARY KEY,
    id_prestador    INT         NOT NULL,
    documento       VARCHAR(20) NOT NULL,
    nombres         VARCHAR(80) NOT NULL,
    apellidos       VARCHAR(80) NOT NULL,
    telefono        VARCHAR(20) NOT NULL,
    tipo_vehiculo   VARCHAR(20) NOT NULL,
    estado          VARCHAR(15) NOT NULL CONSTRAINT df_dom_estado DEFAULT 'disponible',
    CONSTRAINT uq_domiciliario_doc      UNIQUE (documento),
    CONSTRAINT ck_domiciliario_vehiculo CHECK (tipo_vehiculo IN ('motocicleta', 'bicicleta', 'automovil')),
    CONSTRAINT ck_domiciliario_estado   CHECK (estado IN ('disponible', 'en_ruta', 'inactivo')),
    CONSTRAINT fk_domiciliario_prest    FOREIGN KEY (id_prestador) REFERENCES Prestador (id_prestador) ON DELETE NO ACTION
);
GO

CREATE TABLE PlatoEtiqueta (
    id_plato    INT NOT NULL,
    id_etiqueta INT NOT NULL,
    CONSTRAINT pk_platoetiqueta   PRIMARY KEY (id_plato, id_etiqueta),
    CONSTRAINT fk_platoetiq_plato FOREIGN KEY (id_plato) REFERENCES Plato (id_plato) ON DELETE CASCADE,
    CONSTRAINT fk_platoetiq_etiq  FOREIGN KEY (id_etiqueta) REFERENCES Etiqueta (id_etiqueta) ON DELETE NO ACTION
);

CREATE TABLE Reserva (
    id_reserva        INT           CONSTRAINT pk_reserva PRIMARY KEY,
    id_cliente        INT           NOT NULL,
    id_plan           INT           NULL,
    id_experiencia    INT           NULL,
    fecha_creacion    DATE          NOT NULL CONSTRAINT df_res_fcrea DEFAULT CAST(GETDATE() AS DATE),
    fecha_servicio    DATE          NOT NULL,
    franja_horaria    VARCHAR(20)   NOT NULL,
    numero_comensales INT           NOT NULL,
    valor_total       DECIMAL(12,2) NOT NULL,
    canal             VARCHAR(20)   NOT NULL,
    estado            VARCHAR(15)   NOT NULL CONSTRAINT df_res_estado DEFAULT 'pendiente',
    CONSTRAINT ck_reserva_franja     CHECK (franja_horaria IN ('desayuno', 'almuerzo', 'cena')),
    CONSTRAINT ck_reserva_comensales CHECK (numero_comensales > 0),
    CONSTRAINT ck_reserva_valor      CHECK (valor_total >= 0),
    CONSTRAINT ck_reserva_canal      CHECK (canal IN ('web', 'telefono', 'presencial', 'WhatsApp')),
    CONSTRAINT ck_reserva_estado     CHECK (estado IN ('pendiente', 'confirmada', 'en_curso', 'completada', 'cancelada')),
    CONSTRAINT fk_reserva_cliente    FOREIGN KEY (id_cliente) REFERENCES Cliente (id_cliente) ON DELETE NO ACTION,
    CONSTRAINT fk_reserva_plan       FOREIGN KEY (id_plan) REFERENCES [Plan] (id_plan) ON DELETE NO ACTION,
    CONSTRAINT fk_reserva_exper      FOREIGN KEY (id_experiencia) REFERENCES Experiencia (id_experiencia) ON DELETE NO ACTION
);
GO

CREATE TABLE ReservaMesa (
    id_reserva INT NOT NULL,
    id_mesa    INT NOT NULL,
    CONSTRAINT pk_reservamesa     PRIMARY KEY (id_reserva, id_mesa),
    CONSTRAINT fk_resmesa_reserva FOREIGN KEY (id_reserva) REFERENCES Reserva (id_reserva) ON DELETE CASCADE,
    CONSTRAINT fk_resmesa_mesa    FOREIGN KEY (id_mesa) REFERENCES Mesa (id_mesa) ON DELETE NO ACTION
);

CREATE TABLE Pago (
    id_pago                INT           CONSTRAINT pk_pago PRIMARY KEY,
    id_reserva             INT           NOT NULL,
    fecha_pago             DATE          NOT NULL CONSTRAINT df_pago_fecha DEFAULT CAST(GETDATE() AS DATE),
    monto                  DECIMAL(12,2) NOT NULL,
    metodo                 VARCHAR(25)   NOT NULL,
    estado                 VARCHAR(20)   NOT NULL CONSTRAINT df_pago_estado DEFAULT 'aprobado',
    referencia_transaccion VARCHAR(60)   NULL,
    CONSTRAINT ck_pago_monto      CHECK (monto > 0),
    CONSTRAINT ck_pago_metodo     CHECK (metodo IN ('efectivo', 'tarjeta', 'transferencia', 'PSE')),
    CONSTRAINT ck_pago_estado     CHECK (estado IN ('aprobado', 'pendiente', 'rechazado', 'reembolsado_80', 'retenido')),
    CONSTRAINT fk_pago_reserva    FOREIGN KEY (id_reserva) REFERENCES Reserva (id_reserva) ON DELETE NO ACTION
);
CREATE UNIQUE INDEX uq_pago_referencia ON Pago (referencia_transaccion) WHERE referencia_transaccion IS NOT NULL;

CREATE TABLE Notificacion (
    id_notificacion  INT          CONSTRAINT pk_notificacion PRIMARY KEY,
    id_cliente       INT          NOT NULL,
    id_reserva       INT          NULL,
    tipo             VARCHAR(25)  NOT NULL,
    canal            VARCHAR(15)  NOT NULL,
    mensaje          VARCHAR(400) NOT NULL,
    fecha_programada DATE         NOT NULL,
    fecha_real       DATE         NULL,
    estado           VARCHAR(15)  NOT NULL CONSTRAINT df_notif_estado DEFAULT 'pendiente',
    CONSTRAINT ck_notif_tipo    CHECK (tipo IN ('confirmacion', 'recordatorio', 'cancelacion', 'promocion')),
    CONSTRAINT ck_notif_canal   CHECK (canal IN ('correo', 'SMS', 'WhatsApp')),
    CONSTRAINT ck_notif_estado  CHECK (estado IN ('pendiente', 'enviada', 'fallida')),
    CONSTRAINT ck_notif_fechas  CHECK (fecha_real IS NULL OR fecha_real >= fecha_programada),
    CONSTRAINT fk_notif_cliente FOREIGN KEY (id_cliente) REFERENCES Cliente (id_cliente) ON DELETE NO ACTION,
    CONSTRAINT fk_notif_reserva FOREIGN KEY (id_reserva) REFERENCES Reserva (id_reserva) ON DELETE SET NULL
);

CREATE TABLE Pedido (
    id_pedido         INT           CONSTRAINT pk_pedido PRIMARY KEY,
    id_prestador      INT           NOT NULL,
    id_cliente        INT           NULL,
    id_reserva        INT           NULL,
    id_mesa           INT           NULL,
    id_domiciliario   INT           NULL,
    fecha_hora        DATE          NOT NULL CONSTRAINT df_ped_fecha DEFAULT CAST(GETDATE() AS DATE),
    tipo_pedido       VARCHAR(15)   NOT NULL,
    direccion_entrega VARCHAR(180)  NULL,
    costo_envio       DECIMAL(10,2) NOT NULL CONSTRAINT df_ped_envio DEFAULT 0,
    total_pedido      DECIMAL(12,2) NOT NULL,
    estado_pedido     VARCHAR(20)   NOT NULL CONSTRAINT df_ped_estado DEFAULT 'recibido',
    CONSTRAINT ck_pedido_tipo      CHECK (tipo_pedido IN ('en_mesa', 'para_llevar', 'domicilio')),
    CONSTRAINT ck_pedido_envio     CHECK (costo_envio >= 0),
    CONSTRAINT ck_pedido_total     CHECK (total_pedido > 0),
    CONSTRAINT ck_pedido_estado    CHECK (estado_pedido IN ('recibido', 'en_preparacion', 'en_camino', 'entregado', 'cancelado')),
    CONSTRAINT ck_pedido_modalidad CHECK (
        (tipo_pedido = 'domicilio' AND direccion_entrega IS NOT NULL) OR
        (tipo_pedido = 'en_mesa' AND id_mesa IS NOT NULL) OR
        (tipo_pedido = 'para_llevar')
    ),
    CONSTRAINT fk_pedido_prestador    FOREIGN KEY (id_prestador) REFERENCES Prestador (id_prestador) ON DELETE NO ACTION,
    CONSTRAINT fk_pedido_cliente      FOREIGN KEY (id_cliente) REFERENCES Cliente (id_cliente) ON DELETE SET NULL,
    CONSTRAINT fk_pedido_reserva      FOREIGN KEY (id_reserva) REFERENCES Reserva (id_reserva) ON DELETE SET NULL,
    CONSTRAINT fk_pedido_mesa         FOREIGN KEY (id_mesa) REFERENCES Mesa (id_mesa) ON DELETE NO ACTION,
    CONSTRAINT fk_pedido_domiciliario FOREIGN KEY (id_domiciliario) REFERENCES Domiciliario (id_domiciliario) ON DELETE SET NULL
);

CREATE TABLE DetallePedido (
    id_pedido                 INT           NOT NULL,
    num_linea                 INT           NOT NULL,
    id_plato                  INT           NOT NULL,
    cantidad                  INT           NOT NULL,
    precio_unitario_historico DECIMAL(10,2) NOT NULL,
    subtotal                  DECIMAL(12,2) NOT NULL,
    CONSTRAINT pk_detallepedido     PRIMARY KEY (id_pedido, num_linea),
    CONSTRAINT ck_detalle_cantidad  CHECK (cantidad > 0),
    CONSTRAINT ck_detalle_precio    CHECK (precio_unitario_historico > 0),
    CONSTRAINT ck_detalle_subtotal  CHECK (subtotal = cantidad * precio_unitario_historico),
    CONSTRAINT fk_detalle_pedido    FOREIGN KEY (id_pedido) REFERENCES Pedido (id_pedido) ON DELETE CASCADE,
    CONSTRAINT fk_detalle_plato     FOREIGN KEY (id_plato) REFERENCES Plato (id_plato) ON DELETE NO ACTION
);
GO
