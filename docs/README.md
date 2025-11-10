# Documentação do Heimdall MDM

Bem-vindo à documentação completa do projeto Heimdall MDM.

## 📚 Índice

### Documentação Android
- **[QUICKSTART.md](./QUICKSTART.md)** - Guia rápido de início e setup

### Sistemas e Funcionalidades
- **[COMMAND_SYSTEM.md](./COMMAND_SYSTEM.md)** - Sistema robusto de comandos MQTT com ACK e reprocessamento
- **[UPDATE_SYSTEM.md](./UPDATE_SYSTEM.md)** - Sistema de autoinstalação e autoatualização via CloudFront
- **[CLOUDFRONT_SETUP.md](./CLOUDFRONT_SETUP.md)** - Guia completo de configuração do CloudFront com S3

## 🚀 Início Rápido

Para começar rapidamente, consulte o [QUICKSTART.md](./QUICKSTART.md).

## 📖 Documentação Detalhada

### Sistema de Comandos

O Heimdall implementa um sistema robusto de comandos MQTT com:
- ACK em 3 estágios (RECEIVED, PROCESSING, RESULT)
- Reprocessamento automático com backoff exponencial
- Store-and-Forward para cenários offline
- Idempotência de comandos

**📄 [Ver documentação completa](./COMMAND_SYSTEM.md)**

### Sistema de Atualização

Sistema completo para instalação e atualização automática de apps:
- Download via CloudFront
- Instalação silenciosa usando Device Owner
- Verificação de versão
- Logs estruturados

**📄 [Ver documentação completa](./UPDATE_SYSTEM.md)**

### Configuração CloudFront

Guia passo a passo para configurar CloudFront com S3:
- Estrutura do bucket S3
- Configuração de distribuição CloudFront
- Permissões e segurança
- Scripts de automação

**📄 [Ver documentação completa](./CLOUDFRONT_SETUP.md)**

## 🔗 Links Úteis

- [Contexto do Projeto](../.context) - Documentação técnica completa do projeto

## 📝 Estrutura de Documentação

```
docs/
├── README.md              # Este arquivo (índice da documentação)
├── QUICKSTART.md          # Guia rápido de início
├── COMMAND_SYSTEM.md      # Sistema de comandos MQTT
├── UPDATE_SYSTEM.md       # Sistema de atualização de apps
└── CLOUDFRONT_SETUP.md    # Configuração CloudFront com S3
```

## 🤝 Contribuindo

Ao adicionar nova documentação:
1. Crie o arquivo `.md` nesta pasta
2. Atualize este `README.md` com o link
3. Mantenha a formatação consistente
