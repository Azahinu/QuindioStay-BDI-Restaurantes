-- ============================================================================
-- UNIVERSIDAD DEL QUINDIO - INGENIERIA DE SISTEMAS Y COMPUTACION
-- BASES DE DATOS I (12331 - GRUPO 02D) - CLASE 18: VISTAS Y RESULTSET
-- PROYECTO QUINDIOSTAY (DOMINIO 4: RESTAURANTES Y GASTRONOMIA)
-- VISTAS SQL PARA EJECUCION LOCAL DESDE JAVA (JDBC + PREPAREDSTATEMENT + RESULTSET)
-- ============================================================================

DROP VIEW IF EXISTS vw_ReservaDetalle;
CREATE VIEW vw_ReservaDetalle AS
SELECT r.id_reserva,
       c.nombres || ' ' || c.apellidos AS cliente,
       c.correo AS correo_cliente,
       COALESCE(pl.nombre, ex.nombre_experiencia, 'Solo mesa') AS servicio_reservado,
       pr.id_prestador,
       pr.nombre_comercial AS prestador,
       m.nombre AS municipio,
       r.fecha_servicio,
       r.franja_horaria,
       r.numero_comensales,
       r.valor_total,
       r.canal,
       r.estado
FROM Reserva r
INNER JOIN Cliente c ON c.id_cliente = r.id_cliente
LEFT JOIN Plan pl ON pl.id_plan = r.id_plan
LEFT JOIN Experiencia ex ON ex.id_experiencia = r.id_experiencia
LEFT JOIN Prestador pr ON pr.id_prestador = COALESCE(pl.id_prestador, ex.id_prestador)
LEFT JOIN Municipio m ON m.codigo_dane = pr.codigo_dane;

DROP VIEW IF EXISTS vw_TotalPagadoCliente;
CREATE VIEW vw_TotalPagadoCliente AS
SELECT c.id_cliente,
       c.nombres || ' ' || c.apellidos AS cliente,
       COALESCE(SUM(CASE WHEN pg.estado = 'aprobado' THEN pg.monto ELSE 0 END), 0) AS total_pagado,
       COUNT(pg.id_pago) AS num_pagos
FROM Cliente c
INNER JOIN Reserva r ON r.id_cliente = c.id_cliente
INNER JOIN Pago pg ON pg.id_reserva = r.id_reserva
GROUP BY c.id_cliente, c.nombres, c.apellidos;

DROP VIEW IF EXISTS vw_IngresosPrestador;
CREATE VIEW vw_IngresosPrestador AS
SELECT pr.id_prestador,
       pr.nombre_comercial AS prestador,
       COUNT(pg.id_pago) AS num_pagos,
       COALESCE(SUM(CASE WHEN pg.estado = 'aprobado' THEN pg.monto ELSE 0 END), 0) AS total_ingresos
FROM Pago pg
INNER JOIN Reserva r ON r.id_reserva = pg.id_reserva
LEFT JOIN Plan pl ON pl.id_plan = r.id_plan
LEFT JOIN Experiencia ex ON ex.id_experiencia = r.id_experiencia
INNER JOIN Prestador pr ON pr.id_prestador = COALESCE(pl.id_prestador, ex.id_prestador)
GROUP BY pr.id_prestador, pr.nombre_comercial;

-- R-01 (Simple - 1 tabla: Plato)
DROP VIEW IF EXISTS vw_Reporte01_PlatosDisponibles;
CREATE VIEW vw_Reporte01_PlatosDisponibles AS
SELECT id_plato,
       id_prestador,
       id_categoria,
       nombre_plato,
       descripcion,
       precio_actual,
       estado
FROM Plato
WHERE estado = 'disponible';

-- R-02 (Simple - 1 tabla: Mesa)
DROP VIEW IF EXISTS vw_Reporte02_MesasPorZona;
CREATE VIEW vw_Reporte02_MesasPorZona AS
SELECT id_mesa,
       id_prestador,
       numero_mesa,
       zona,
       capacidad,
       estado
FROM Mesa
WHERE estado <> 'fuera_de_servicio';

-- R-03 (Simple - 1 tabla: Domiciliario)
DROP VIEW IF EXISTS vw_Reporte03_DomiciliariosActivos;
CREATE VIEW vw_Reporte03_DomiciliariosActivos AS
SELECT id_domiciliario,
       id_prestador,
       documento,
       nombres,
       apellidos,
       telefono,
       tipo_vehiculo,
       estado
FROM Domiciliario
WHERE estado IN ('disponible', 'en_ruta');

-- R-04 Base (Multitabla: DetallePedido, Pedido, Plato, CategoriaPlato)
DROP VIEW IF EXISTS vw_Reporte04_BaseVentas;
CREATE VIEW vw_Reporte04_BaseVentas AS
SELECT c.id_categoria,
       c.nombre_categoria,
       pl.id_plato,
       pl.nombre_plato,
       pe.id_pedido,
       pe.id_prestador,
       pe.fecha_hora,
       dp.cantidad,
       dp.precio_unitario_historico,
       dp.subtotal
FROM DetallePedido dp
INNER JOIN Pedido pe ON dp.id_pedido = pe.id_pedido
INNER JOIN Plato pl ON dp.id_plato = pl.id_plato
INNER JOIN CategoriaPlato c ON pl.id_categoria = c.id_categoria
WHERE pe.estado_pedido = 'entregado';

DROP VIEW IF EXISTS vw_Reporte04_PlatosMasVendidos;
CREATE VIEW vw_Reporte04_PlatosMasVendidos AS
SELECT nombre_categoria,
       nombre_plato,
       COUNT(id_pedido) AS comandas_donde_aparece,
       SUM(cantidad) AS unidades_vendidas,
       ROUND(AVG(precio_unitario_historico)) AS precio_promedio_cobrado,
       SUM(subtotal) AS ingresos_historicos
FROM vw_Reporte04_BaseVentas
GROUP BY nombre_categoria, nombre_plato
HAVING SUM(cantidad) >= 15;

-- R-05 (Multitabla: Reserva, ReservaMesa, Mesa, Prestador + Dia semana de fecha_servicio y franja_horaria)
DROP VIEW IF EXISTS vw_Reporte05_OcupacionFranjaDia;
CREATE VIEW vw_Reporte05_OcupacionFranjaDia AS
SELECT pr.id_prestador,
       pr.nombre_comercial,
       CASE CAST(strftime('%w', r.fecha_servicio) AS INT)
           WHEN 0 THEN 'Domingo'
           WHEN 1 THEN 'Lunes'
           WHEN 2 THEN 'Martes'
           WHEN 3 THEN 'Miercoles'
           WHEN 4 THEN 'Jueves'
           WHEN 5 THEN 'Viernes'
           WHEN 6 THEN 'Sabado'
       END AS dia_semana,
       CAST(strftime('%w', r.fecha_servicio) AS INT) AS num_dia,
       r.franja_horaria,
       COUNT(DISTINCT r.id_reserva) AS total_reservas,
       COUNT(rm.id_mesa) AS mesas_acopladas,
       SUM(r.numero_comensales) AS total_comensales
FROM Reserva r
INNER JOIN ReservaMesa rm ON r.id_reserva = rm.id_reserva
INNER JOIN Mesa m ON rm.id_mesa = m.id_mesa
INNER JOIN Prestador pr ON m.id_prestador = pr.id_prestador
WHERE r.estado <> 'cancelada'
GROUP BY pr.id_prestador, pr.nombre_comercial, dia_semana, num_dia, r.franja_horaria;

-- R-06 (Multitabla: Pedido, Prestador, Municipio)
DROP VIEW IF EXISTS vw_Reporte06_IngresosModalidad;
CREATE VIEW vw_Reporte06_IngresosModalidad AS
SELECT m.nombre AS municipio,
       pr.id_prestador,
       pr.nombre_comercial,
       pe.tipo_pedido,
       COUNT(pe.id_pedido) AS cantidad_pedidos,
       ROUND(AVG(pe.total_pedido)) AS ticket_promedio,
       SUM(pe.total_pedido) AS recaudo_total
FROM Pedido pe
INNER JOIN Prestador pr ON pe.id_prestador = pr.id_prestador
INNER JOIN Municipio m ON pr.codigo_dane = m.codigo_dane
WHERE pe.estado_pedido = 'entregado'
GROUP BY m.nombre, pr.id_prestador, pr.nombre_comercial, pe.tipo_pedido
HAVING SUM(pe.total_pedido) > 100000;

-- R-07 (Multitabla: Pedido, Domiciliario, Prestador - incluye entregas y cancelados)
DROP VIEW IF EXISTS vw_Reporte07_EntregasDomiciliario;
CREATE VIEW vw_Reporte07_EntregasDomiciliario AS
SELECT d.id_domiciliario,
       d.documento,
       d.nombres || ' ' || d.apellidos AS domiciliario,
       d.tipo_vehiculo,
       pr.nombre_comercial AS restaurante_base,
       SUM(CASE WHEN pe.estado_pedido = 'entregado' THEN 1 ELSE 0 END) AS entregas_realizadas,
       SUM(CASE WHEN pe.estado_pedido = 'cancelado' THEN 1 ELSE 0 END) AS domicilios_cancelados,
       COALESCE(SUM(CASE WHEN pe.estado_pedido = 'entregado' THEN pe.costo_envio ELSE 0 END), 0) AS total_envios_cobrados,
       COALESCE(SUM(CASE WHEN pe.estado_pedido = 'entregado' THEN pe.total_pedido ELSE 0 END), 0) AS valor_pedidos_transportados
FROM Domiciliario d
INNER JOIN Prestador pr ON d.id_prestador = pr.id_prestador
LEFT JOIN Pedido pe ON d.id_domiciliario = pe.id_domiciliario AND pe.tipo_pedido = 'domicilio'
GROUP BY d.id_domiciliario, d.documento, d.nombres, d.apellidos, d.tipo_vehiculo, pr.nombre_comercial;

-- R-08 (Avanzado: Subconsulta correlacionada por categoria + NOT EXISTS)
DROP VIEW IF EXISTS vw_Reporte08_PlatosBajoPromedioCategoria;
CREATE VIEW vw_Reporte08_PlatosBajoPromedioCategoria AS
SELECT c.id_categoria,
       c.nombre_categoria,
       pl.id_plato,
       pl.nombre_plato,
       pr.nombre_comercial,
       pl.precio_actual,
       COALESCE(SUM(dp.cantidad), 0) AS unidades_vendidas,
       COALESCE(SUM(dp.subtotal), 0) AS ingresos_acumulados,
       ROUND((
           SELECT COALESCE(AVG(ing), 0)
           FROM (
               SELECT COALESCE(SUM(dp2.subtotal), 0) AS ing
               FROM Plato p2
               LEFT JOIN DetallePedido dp2 ON p2.id_plato = dp2.id_plato
               WHERE p2.id_categoria = c.id_categoria
               GROUP BY p2.id_plato
           )
       ), 0) AS promedio_ingresos_categoria
FROM Plato pl
INNER JOIN CategoriaPlato c ON pl.id_categoria = c.id_categoria
INNER JOIN Prestador pr ON pl.id_prestador = pr.id_prestador
LEFT JOIN DetallePedido dp ON pl.id_plato = dp.id_plato
GROUP BY c.id_categoria, c.nombre_categoria, pl.id_plato, pl.nombre_plato, pr.nombre_comercial, pl.precio_actual
HAVING NOT EXISTS (SELECT 1 FROM DetallePedido dp0 WHERE dp0.id_plato = pl.id_plato)
    OR COALESCE(SUM(dp.subtotal), 0) < (
        SELECT COALESCE(AVG(ing), 0)
        FROM (
            SELECT COALESCE(SUM(dp2.subtotal), 0) AS ing
            FROM Plato p2
            LEFT JOIN DetallePedido dp2 ON p2.id_plato = dp2.id_plato
            WHERE p2.id_categoria = c.id_categoria
            GROUP BY p2.id_plato
        )
    );

-- R-09 (Avanzado: Clientes con consumo combinado Reservas + Pedidos > promedio plataforma, con PerfilCliente)
DROP VIEW IF EXISTS vw_Reporte09_ClientesFrecuentes;
CREATE VIEW vw_Reporte09_ClientesFrecuentes AS
SELECT c.id_cliente,
       c.tipo_documento || ' ' || c.numero_documento AS documento,
       c.nombres || ' ' || c.apellidos AS cliente,
       c.ciudad_origen,
       pc.canal_preferido,
       CASE WHEN pc.acepta_promociones = 1 THEN 'Si' ELSE 'No' END AS acepta_promociones,
       COALESCE(res_pag.num_reservas, 0) AS reservas_pagadas,
       COALESCE(res_pag.total_reservas, 0) AS gastado_reservas,
       COALESCE(ped_ent.num_pedidos, 0) AS pedidos_entregados,
       COALESCE(ped_ent.total_pedidos, 0) AS gastado_pedidos,
       (COALESCE(res_pag.total_reservas, 0) + COALESCE(ped_ent.total_pedidos, 0)) AS gasto_total_plataforma
FROM Cliente c
INNER JOIN PerfilCliente pc ON c.id_cliente = pc.id_cliente
LEFT JOIN (
    SELECT r.id_cliente, COUNT(DISTINCT r.id_reserva) AS num_reservas, SUM(pg.monto) AS total_reservas
    FROM Reserva r
    INNER JOIN Pago pg ON r.id_reserva = pg.id_reserva
    WHERE pg.estado = 'aprobado'
    GROUP BY r.id_cliente
) res_pag ON res_pag.id_cliente = c.id_cliente
LEFT JOIN (
    SELECT pe.id_cliente, COUNT(pe.id_pedido) AS num_pedidos, SUM(pe.total_pedido) AS total_pedidos
    FROM Pedido pe
    WHERE pe.estado_pedido = 'entregado' AND pe.id_cliente IS NOT NULL
    GROUP BY pe.id_cliente
) ped_ent ON ped_ent.id_cliente = c.id_cliente
WHERE (COALESCE(res_pag.total_reservas, 0) + COALESCE(ped_ent.total_pedidos, 0)) > (
    SELECT AVG(gasto_total)
    FROM (
        SELECT c2.id_cliente,
               COALESCE((SELECT SUM(pg2.monto) FROM Reserva r2 JOIN Pago pg2 ON r2.id_reserva = pg2.id_reserva WHERE r2.id_cliente = c2.id_cliente AND pg2.estado = 'aprobado'), 0)
             + COALESCE((SELECT SUM(pe2.total_pedido) FROM Pedido pe2 WHERE pe2.id_cliente = c2.id_cliente AND pe2.estado_pedido = 'entregado'), 0) AS gasto_total
        FROM Cliente c2
    )
);

-- R-10 (Avanzado: Tasa de cancelacion % por restaurante > promedio del Quindio + regla 80%)
DROP VIEW IF EXISTS vw_Reporte10_RestaurantesCancelaciones;
CREATE VIEW vw_Reporte10_RestaurantesCancelaciones AS
SELECT base.municipio,
       base.id_prestador,
       base.nombre_comercial,
       base.total_reservas,
       base.reservas_canceladas,
       base.tasa_cancelacion_pct,
       base.total_reembolsado_80,
       base.total_retenido
FROM (
    SELECT m.nombre AS municipio,
           pr.id_prestador,
           pr.nombre_comercial,
           COUNT(DISTINCT r.id_reserva) AS total_reservas,
           COUNT(DISTINCT CASE WHEN r.estado = 'cancelada' THEN r.id_reserva END) AS reservas_canceladas,
           ROUND(100.0 * COUNT(DISTINCT CASE WHEN r.estado = 'cancelada' THEN r.id_reserva END) / COUNT(DISTINCT r.id_reserva), 2) AS tasa_cancelacion_pct,
           COALESCE(SUM(CASE WHEN r.estado = 'cancelada' AND pg.estado = 'reembolsado_80' THEN pg.monto ELSE 0 END), 0) AS total_reembolsado_80,
           COALESCE(SUM(CASE WHEN r.estado = 'cancelada' AND pg.estado = 'retenido' THEN pg.monto ELSE 0 END), 0) AS total_retenido
    FROM Prestador pr
    INNER JOIN Municipio m ON pr.codigo_dane = m.codigo_dane
    INNER JOIN Plan pl ON pr.id_prestador = pl.id_prestador
    INNER JOIN Reserva r ON pl.id_plan = r.id_plan
    LEFT JOIN Pago pg ON r.id_reserva = pg.id_reserva
    GROUP BY m.nombre, pr.id_prestador, pr.nombre_comercial
) base
WHERE base.tasa_cancelacion_pct > (
    SELECT AVG(sub.tasa)
    FROM (
        SELECT 100.0 * SUM(CASE WHEN r2.estado = 'cancelada' THEN 1 ELSE 0 END) / COUNT(*) AS tasa
        FROM Plan pl2
        INNER JOIN Reserva r2 ON pl2.id_plan = r2.id_plan
        GROUP BY pl2.id_prestador
    ) sub
);

-- Defensas de Sustentacion (Clase 18 + Entrega 2 Sec 1.2)
DROP VIEW IF EXISTS vw_Defensa_GruposGrandes;
CREATE VIEW vw_Defensa_GruposGrandes AS
SELECT r.id_reserva,
       c.nombres || ' ' || c.apellidos AS cliente,
       pr.nombre_comercial AS restaurante,
       r.fecha_servicio,
       r.franja_horaria,
       r.numero_comensales,
       COUNT(rm.id_mesa) AS cantidad_mesas_juntadas,
       SUM(m.capacidad) AS capacidad_total_sillas
FROM Reserva r
INNER JOIN Cliente c ON r.id_cliente = c.id_cliente
INNER JOIN ReservaMesa rm ON r.id_reserva = rm.id_reserva
INNER JOIN Mesa m ON rm.id_mesa = m.id_mesa
INNER JOIN Prestador pr ON m.id_prestador = pr.id_prestador
WHERE r.numero_comensales > 12
GROUP BY r.id_reserva, c.nombres, c.apellidos, pr.nombre_comercial, r.fecha_servicio, r.franja_horaria, r.numero_comensales;

DROP VIEW IF EXISTS vw_Defensa_PrecioHistorico;
CREATE VIEW vw_Defensa_PrecioHistorico AS
SELECT pe.id_pedido,
       dp.num_linea,
       pr.nombre_comercial,
       m.nombre AS municipio,
       c.nombres || ' ' || c.apellidos AS cliente,
       pl.nombre_plato,
       dp.cantidad,
       dp.precio_unitario_historico AS precio_cobrado_ese_dia,
       pl.precio_actual AS precio_hoy_en_carta,
       dp.subtotal
FROM DetallePedido dp
INNER JOIN Pedido pe ON dp.id_pedido = pe.id_pedido
INNER JOIN Plato pl ON dp.id_plato = pl.id_plato
INNER JOIN Prestador pr ON pe.id_prestador = pr.id_prestador
INNER JOIN Municipio m ON pr.codigo_dane = m.codigo_dane
LEFT JOIN Cliente c ON pe.id_cliente = c.id_cliente;
