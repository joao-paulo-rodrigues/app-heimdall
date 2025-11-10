# Configuração do CloudFront com S3 para Distribuição de APKs

Este guia explica como configurar uma distribuição CloudFront para servir APKs armazenados no S3, permitindo downloads rápidos e confiáveis para o sistema de atualização do Heimdall.

## Pré-requisitos

- Conta AWS com acesso ao S3 e CloudFront
- AWS CLI configurado (opcional, mas recomendado)
- Bucket S3 criado

## Passo 1: Estrutura do Bucket S3

### 1.1 Criar Estrutura de Pastas

Organize os APKs no S3 seguindo esta estrutura:

```
s3://seu-bucket/apps/
├── com.uebrasil.panicbuttonapp/
│   ├── manifest.json
│   ├── app.apk (latest)
│   └── 1.0.0/
│       └── app.apk
├── com.heimdall.device/
│   ├── manifest.json
│   ├── app.apk (latest)
│   └── 1.0.0/
│       └── app.apk
└── com.example.app/
    ├── manifest.json
    └── ...
```

### 1.2 Criar Manifest JSON

Para cada app, crie um arquivo `manifest.json` na raiz do pacote:

**Exemplo: `s3://seu-bucket/apps/com.uebrasil.panicbuttonapp/manifest.json`**

```json
{
  "latest": {
    "package_name": "com.uebrasil.panicbuttonapp",
    "version_name": "1.0.0",
    "version_code": 1,
    "download_url": "https://d1234567890.cloudfront.net/apps/com.uebrasil.panicbuttonapp/1.0.0/app.apk",
    "checksum": "sha256:a1b2c3d4e5f6...",
    "min_sdk_version": 28,
    "target_sdk_version": 34,
    "release_notes": "Initial release",
    "published_at": 1704067200000
  },
  "versions": [
    {
      "package_name": "com.uebrasil.panicbuttonapp",
      "version_name": "1.0.0",
      "version_code": 1,
      "download_url": "https://d1234567890.cloudfront.net/apps/com.uebrasil.panicbuttonapp/1.0.0/app.apk",
      "checksum": "sha256:a1b2c3d4e5f6...",
      "min_sdk_version": 28,
      "target_sdk_version": 34,
      "release_notes": "Initial release",
      "published_at": 1704067200000
    }
  ]
}
```

### 1.3 Calcular Checksum SHA-256

Para calcular o checksum do APK:

```bash
# Linux/Mac
sha256sum app.apk | awk '{print "sha256:"$1}'

# Ou usando OpenSSL
openssl dgst -sha256 -binary app.apk | xxd -p -c 256 | sed 's/^/sha256:/'
```

## Passo 2: Configurar Permissões do Bucket S3

### 2.1 Política de Bucket (Bucket Policy)

Configure a política do bucket para permitir acesso público via CloudFront:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "AllowCloudFrontAccess",
      "Effect": "Allow",
      "Principal": {
        "Service": "cloudfront.amazonaws.com"
      },
      "Action": "s3:GetObject",
      "Resource": "arn:aws:s3:::seu-bucket/*",
      "Condition": {
        "StringEquals": {
          "AWS:SourceArn": "arn:aws:cloudfront::ACCOUNT_ID:distribution/DISTRIBUTION_ID"
        }
      }
    }
  ]
}
```

**Nota**: Substitua `ACCOUNT_ID` e `DISTRIBUTION_ID` após criar a distribuição CloudFront.

### 2.2 Bloquear Acesso Público Direto (Recomendado)

Mantenha o bucket privado e permita acesso apenas via CloudFront:

1. No console S3, vá em **Permissions** → **Block public access**
2. Mantenha todas as opções marcadas (bloquear acesso público)
3. O acesso será feito apenas via CloudFront com OAC (Origin Access Control)

## Passo 3: Criar Distribuição CloudFront

### 3.1 Via Console AWS

1. Acesse o **CloudFront Console**
2. Clique em **Create Distribution**
3. Configure:

   **Origin Settings:**
   - **Origin Domain**: Selecione seu bucket S3 (ex: `seu-bucket.s3.amazonaws.com`)
   - **Origin Path**: `/apps` (se os apps estão na pasta `/apps`)
   - **Name**: Nome automático (ou personalizado)
   - **Origin Access**: Selecione **Origin Access Control settings (recommended)**
   - Clique em **Create Control Setting**:
     - **Control setting name**: `s3-oac-heimdall`
     - **Signing behavior**: **Sign requests (recommended)**
     - **Origin type**: **S3**
     - Clique em **Create**
   - **Bucket policy**: CloudFront criará uma política automaticamente (copie e aplique no bucket)

   **Default Cache Behavior:**
   - **Viewer Protocol Policy**: **Redirect HTTP to HTTPS** (recomendado) ou **Allow all viewer protocols**
   - **Allowed HTTP Methods**: **GET, HEAD, OPTIONS**
   - **Cache Policy**: **CachingOptimized** (ou **CachingDisabled** para desenvolvimento)
   - **Origin Request Policy**: **CORS-S3Origin** (se necessário)

   **Distribution Settings:**
   - **Price Class**: Escolha conforme sua necessidade
   - **Alternate Domain Names (CNAMEs)**: Opcional (ex: `apps.seudominio.com`)
   - **SSL Certificate**: Se usar CNAME, configure certificado SSL
   - **Default Root Object**: Deixe em branco

4. Clique em **Create Distribution**

### 3.2 Aplicar Política no Bucket

Após criar a distribuição, o CloudFront fornecerá uma política de bucket. Aplique-a:

1. Copie a política fornecida pelo CloudFront
2. No console S3, vá em **Permissions** → **Bucket Policy**
3. Cole a política e salve

**Exemplo de política gerada:**

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "AllowCloudFrontServicePrincipal",
      "Effect": "Allow",
      "Principal": {
        "Service": "cloudfront.amazonaws.com"
      },
      "Action": "s3:GetObject",
      "Resource": "arn:aws:s3:::seu-bucket/*",
      "Condition": {
        "StringEquals": {
          "AWS:SourceArn": "arn:aws:cloudfront::123456789012:distribution/E1234567890ABC"
        }
      }
    }
  ]
}
```

## Passo 4: Configurar Headers e CORS (Opcional)

### 4.1 Headers Customizados

Se necessário, configure headers customizados no CloudFront:

1. Vá em **Behaviors** → Edite o comportamento padrão
2. Em **Response Headers Policy**, crie uma nova política:
   - **Content-Type**: `application/vnd.android.package-archive`
   - **Content-Disposition**: `attachment; filename="app.apk"`

### 4.2 CORS (Se necessário)

Se precisar de CORS, configure no bucket S3:

**CORS Configuration** (`s3://seu-bucket/`):

```json
[
  {
    "AllowedHeaders": ["*"],
    "AllowedMethods": ["GET", "HEAD"],
    "AllowedOrigins": ["*"],
    "ExposeHeaders": ["ETag"],
    "MaxAgeSeconds": 3000
  }
]
```

## Passo 5: Configurar Cache (Recomendado)

### 5.1 Cache Policy para APKs

Para APKs, recomenda-se cache mínimo (ou desabilitado) para garantir sempre a versão mais recente:

1. Vá em **Policies** → **Cache Policies** → **Create cache policy**
2. Configure:
   - **Name**: `heimdall-apk-cache`
   - **TTL**: **Minimum TTL**: 0, **Maximum TTL**: 0, **Default TTL**: 0
   - **Cache key settings**: Inclua `Host` e `Query-String`
3. Aplique esta política ao comportamento padrão

### 5.2 Invalidação de Cache

Quando atualizar um APK, invalide o cache:

```bash
# Via AWS CLI
aws cloudfront create-invalidation \
  --distribution-id E1234567890ABC \
  --paths "/apps/com.uebrasil.panicbuttonapp/*"
```

Ou via Console:
1. Vá em **Invalidations** → **Create invalidation**
2. Digite os paths: `/apps/com.uebrasil.panicbuttonapp/*`
3. Clique em **Create invalidation**

## Passo 6: Atualizar Configuração no Heimdall

### 6.1 Atualizar UpdateConfig.kt

Edite `android/app/src/main/java/com/heimdall/device/update/UpdateConfig.kt`:

```kotlin
object UpdateConfig {
    // Substitua pela URL do seu CloudFront
    const val CLOUDFRONT_BASE_URL = "https://d1234567890.cloudfront.net"
    
    // ... resto do código
}
```

### 6.2 Testar Download

Teste o download diretamente:

```bash
# Testar manifest
curl https://d1234567890.cloudfront.net/apps/com.uebrasil.panicbuttonapp/manifest.json

# Testar download do APK
curl -I https://d1234567890.cloudfront.net/apps/com.uebrasil.panicbuttonapp/app.apk
```

## Passo 7: Script de Upload Automático (Opcional)

Crie um script para facilitar o upload de novos APKs:

```bash
#!/bin/bash
# upload-apk.sh

BUCKET="seu-bucket"
PACKAGE_NAME="$1"
VERSION="$2"
APK_FILE="$3"

if [ -z "$PACKAGE_NAME" ] || [ -z "$VERSION" ] || [ -z "$APK_FILE" ]; then
    echo "Uso: $0 <package_name> <version> <apk_file>"
    exit 1
fi

# Calcular checksum
CHECKSUM=$(sha256sum "$APK_FILE" | awk '{print "sha256:"$1}')

# Upload APK
aws s3 cp "$APK_FILE" "s3://$BUCKET/apps/$PACKAGE_NAME/$VERSION/app.apk"

# Upload como latest
aws s3 cp "$APK_FILE" "s3://$BUCKET/apps/$PACKAGE_NAME/app.apk"

# Atualizar manifest (requer script Python/Node para gerar JSON)
# ...

# Invalidar cache
DISTRIBUTION_ID="E1234567890ABC"
aws cloudfront create-invalidation \
  --distribution-id "$DISTRIBUTION_ID" \
  --paths "/apps/$PACKAGE_NAME/*"

echo "Upload concluído!"
```

## Passo 8: Monitoramento e Logs

### 8.1 Habilitar Logs do CloudFront

1. Vá em **Settings** da distribuição
2. Em **Standard logging**, configure:
   - **Bucket for logs**: Selecione um bucket para logs
   - **Log prefix**: `cloudfront-logs/`

### 8.2 Métricas

Monitore métricas importantes:
- **Requests**: Número de requisições
- **Data Transfer**: Transferência de dados
- **Error Rate**: Taxa de erros (4xx, 5xx)
- **Cache Hit Rate**: Taxa de acerto no cache

## Troubleshooting

### Erro 403 Forbidden

- Verifique a política do bucket S3
- Verifique se o Origin Access Control está configurado corretamente
- Verifique se a política do bucket referencia o ARN correto do CloudFront

### Erro 404 Not Found

- Verifique se o arquivo existe no S3
- Verifique o **Origin Path** na configuração do CloudFront
- Verifique se o path no código está correto

### Download Lento

- Verifique a **Price Class** (pode estar limitada a uma região)
- Considere usar **CloudFront Functions** para otimização
- Verifique se o cache está configurado corretamente

### Cache Não Atualiza

- Crie uma invalidação manual
- Verifique a **Cache Policy** (pode estar com TTL muito alto)
- Considere usar versionamento de arquivos (incluir versão no nome)

## Custos Estimados

- **S3 Storage**: ~$0.023/GB/mês
- **S3 Requests**: ~$0.0004 por 1.000 requisições GET
- **CloudFront Data Transfer**: ~$0.085/GB (primeiros 10TB)
- **CloudFront Requests**: ~$0.0075 por 10.000 requisições HTTPS

**Exemplo**: 1000 dispositivos baixando 10MB cada = 10GB = ~$0.85 + custos de requisições

## Segurança

### Recomendações

1. **Mantenha o bucket privado**: Use apenas CloudFront para acesso
2. **Use HTTPS**: Configure redirect HTTP → HTTPS
3. **Valide checksums**: O Heimdall valida checksums SHA-256
4. **Monitore acessos**: Use CloudFront logs para auditoria
5. **Limite por IP**: Configure WAF se necessário

### WAF (Web Application Firewall) - Opcional

Para proteção adicional:

1. Crie uma **Web ACL** no WAF
2. Configure regras:
   - Rate limiting por IP
   - Bloqueio de geolocalização (se necessário)
   - Proteção contra DDoS
3. Associe ao CloudFront

## Próximos Passos

1. ✅ Configurar bucket S3 com estrutura de pastas
2. ✅ Criar distribuição CloudFront
3. ✅ Aplicar políticas de acesso
4. ✅ Testar downloads
5. ✅ Atualizar `UpdateConfig.kt` com a URL do CloudFront
6. ✅ Testar instalação via comando MQTT

## Referências

- [CloudFront Documentation](https://docs.aws.amazon.com/cloudfront/)
- [S3 Documentation](https://docs.aws.amazon.com/s3/)
- [Origin Access Control](https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/private-content-restricting-access-to-s3.html)

