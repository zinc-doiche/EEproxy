package zinc.doiche

import com.google.inject.Inject
import com.querydsl.jpa.impl.JPAQueryFactory
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent
import com.velocitypowered.api.plugin.Plugin
import com.velocitypowered.api.plugin.annotation.DataDirectory
import com.velocitypowered.api.proxy.ProxyServer
import jakarta.persistence.EntityManager
import org.slf4j.Logger
import redis.clients.jedis.JedisPooled
import zinc.doiche.database.CachePoolFactory
import zinc.doiche.database.DatabaseFactoryProvider
import zinc.doiche.lib.init.ClassLoader
import zinc.doiche.lib.init.ProcessorFactory
import zinc.doiche.service.Service
import zinc.doiche.util.LoggerProvider
import java.nio.file.Path

@Plugin(id = "ee-proxy", name = "EEProxy", version = "alpha", authors = ["Doiche"])
class EEProxy @Inject constructor(
    val proxyServer: ProxyServer,

    @DataDirectory
    val dataDirectory: Path,

    logger: Logger,
) {
    companion object {
        internal lateinit var proxy: EEProxy
            private set
    }

    val jedisPooled: JedisPooled
    val entityManager: EntityManager
    val jpaQueryFactory: JPAQueryFactory
    private val services: MutableList<Service> = mutableListOf()

    init {
        LoggerProvider.init(logger)
        jedisPooled = CachePoolFactory().create() ?: throw IllegalStateException("jedis pooled is null")
        entityManager = DatabaseFactoryProvider.create()?.createEntityManager() ?: throw IllegalStateException("factory is null")
        jpaQueryFactory = JPAQueryFactory(entityManager)
    }

    @Subscribe
    fun onProxyInitialization(event: ProxyInitializeEvent) {
        proxy = this
        proxyServer.eventManager.register(this, this)

        process()
        loadServices()
    }

    private fun process() {
        ClassLoader()
            .add(ProcessorFactory.translatable {
                it.replace("<brace>", "[")
                    .replace("</brace>", "]")
                    .replace("<curlyBrace>", "{")
                    .replace("</curlyBrace>", "}")
            })
            .add(ProcessorFactory.service())
            .add(ProcessorFactory.listener(this, proxyServer.eventManager))
            .add(ProcessorFactory.command(proxyServer.commandManager))
            .process()
    }

    private fun loadServices() {
        services.forEach(Service::onEnable)
    }

    fun register(vararg service: Service) {
        services.addAll(service)
    }
}


