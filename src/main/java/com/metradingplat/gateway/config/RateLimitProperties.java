package com.metradingplat.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Propiedades configurables para el rate limiting del gateway.
 * Se leen desde application.yml bajo el prefijo gateway.rate-limit
 */
@Component
@ConfigurationProperties(prefix = "gateway.rate-limit")
public class RateLimitProperties {

    /** Si el rate limiting está habilitado */
    private boolean habilitado = true;

    /** Máximo de peticiones permitidas por minuto antes de responder 429 */
    private int limitePeticionesPorMinuto = 120;

    /** Umbral de peticiones por minuto para banear la IP automáticamente */
    private int umbralBaneoAutomatico = 300;

    /** Duración del baneo en minutos */
    private int duracionBaneoMinutos = 10;

    /** Tiempo de expiración del bucket por inactividad (minutos) */
    private int expiracionBucketMinutos = 15;

    /** Tamaño máximo del cache de buckets (número de IPs distintas) */
    private int maximoIpsEnCache = 50000;

    public boolean isHabilitado() { return habilitado; }
    public void setHabilitado(boolean habilitado) { this.habilitado = habilitado; }

    public int getLimitePeticionesPorMinuto() { return limitePeticionesPorMinuto; }
    public void setLimitePeticionesPorMinuto(int limitePeticionesPorMinuto) { this.limitePeticionesPorMinuto = limitePeticionesPorMinuto; }

    public int getUmbralBaneoAutomatico() { return umbralBaneoAutomatico; }
    public void setUmbralBaneoAutomatico(int umbralBaneoAutomatico) { this.umbralBaneoAutomatico = umbralBaneoAutomatico; }

    public int getDuracionBaneoMinutos() { return duracionBaneoMinutos; }
    public void setDuracionBaneoMinutos(int duracionBaneoMinutos) { this.duracionBaneoMinutos = duracionBaneoMinutos; }

    public int getExpiracionBucketMinutos() { return expiracionBucketMinutos; }
    public void setExpiracionBucketMinutos(int expiracionBucketMinutos) { this.expiracionBucketMinutos = expiracionBucketMinutos; }

    public int getMaximoIpsEnCache() { return maximoIpsEnCache; }
    public void setMaximoIpsEnCache(int maximoIpsEnCache) { this.maximoIpsEnCache = maximoIpsEnCache; }
}
