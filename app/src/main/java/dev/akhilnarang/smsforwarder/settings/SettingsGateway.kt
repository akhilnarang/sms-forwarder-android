package dev.akhilnarang.smsforwarder.settings

interface SettingsGateway {
    fun currentSettings(): AppSettings
}
