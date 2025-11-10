package com.heimdall.device

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.heimdall.device.init.FirstRunManager
import com.heimdall.device.service.MqttServiceManager
import com.heimdall.device.util.Logger

/**
 * MainActivity headless - sem interface visual
 * Apenas exibe toast informando que o Heimdall está rodando e fecha imediatamente
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Não definir layout - app headless
        // setContentView() não é chamado
        
        Logger.info(
            component = "heimdall.device.activity",
            message = "MainActivity created - headless mode"
        )
        
        // Exibir toast informando que está rodando
        Toast.makeText(
            this,
            "Heimdall MDM está rodando",
            Toast.LENGTH_SHORT
        ).show()
        
        // Inicializar serviços
        val mqttManager = MqttServiceManager.getInstance(this)
        mqttManager.connect()
        
        Logger.info(
            component = "heimdall.device.activity",
            message = "Heimdall services initialized"
        )
        
        // Verificar e executar primeira execução se necessário
        val firstRunManager = FirstRunManager.getInstance(this)
        if (firstRunManager.isFirstRun() && !firstRunManager.isFirstRunCompleted()) {
            Logger.info(
                component = "heimdall.device.activity",
                message = "First run detected - starting cleanup process"
            )
            
            firstRunManager.executeFirstRun { success ->
                if (success) {
                    Logger.info(
                        component = "heimdall.device.activity",
                        message = "First run completed successfully"
                    )
                } else {
                    Logger.warning(
                        component = "heimdall.device.activity",
                        message = "First run completed with errors"
                    )
                }
            }
        }
        
        // Fechar activity imediatamente - app roda em background
        finish()
    }
}

