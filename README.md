# Práctica S11: Persistencia y comunicación multi-componente

Ejemplo de una API Spring WebFlux que guarda y consulta notas en MongoDB dentro del namespace `practica-s11`. MongoDB usa un PVC; las opciones no sensibles se inyectan desde un ConfigMap y las credenciales desde un Secret.

## Arquitectura

```text
Cliente --HTTP/NodePort asignado por Kubernetes--> backend (Spring WebFlux)
                                      |
                                ClusterIP: mongo:27017
                                      |
                               MongoDB -- /data/db --> mongo-pvc
```

La API implementa `GET /api/notes`, `GET /api/notes/{id}` y `POST /api/notes` de manera reactiva. El puerto externo se expone como NodePort; Kubernetes asigna uno libre al crear el Service.

## Requisitos

- Java 17 y Maven 3.8 o superior.
- Docker y un clúster Kubernetes local (por ejemplo, Docker Desktop, Minikube o kind).
- Un StorageClass predeterminado en el clúster para aprovisionar el PVC dinámicamente. Compruébalo con `kubectl get storageclass`.

## Construir y desplegar

Desde la raíz del proyecto:

```bash
mvn clean package
docker build -t practica-webflux:1.0.0 .
```

Haz que la imagen esté disponible en el clúster local:

```bash
# Minikube
minikube image load practica-webflux:1.0.0

# kind (usa esta alternativa en lugar del comando de Minikube)
kind load docker-image practica-webflux:1.0.0
```

En Docker Desktop, normalmente basta con construir la imagen en el daemon que usa el clúster. Para un clúster remoto, publica la imagen en un registro y actualiza `image` en `k8s/05-backend-stack.yaml`.

Aplica los manifiestos y espera a que los despliegues estén disponibles:

```bash
kubectl apply -f k8s/
kubectl rollout status deployment/mongo -n practica-s11
kubectl rollout status deployment/backend -n practica-s11
```

`kubectl apply -f k8s/` procesa los archivos en orden alfabético: Namespace, PVC, ConfigMap, Secret, MongoDB y backend.

## Credenciales y seguridad

`k8s/03-db-secret.yaml` contiene credenciales **de demostración** codificadas en Base64 (`root` / `cambia-esto-antes-de-usar`) para que el laboratorio funcione de inmediato. Base64 no cifra ni protege el secreto. No uses estas credenciales en producción ni publiques credenciales reales en GitHub. Para cambiar el ejemplo, genera ambos valores Base64 con:

```bash
printf '%s' 'usuario-de-laboratorio' | base64
printf '%s' 'una-clave-distinta' | base64
```

Reemplaza en el Secret los valores `MONGO_INITDB_ROOT_USERNAME` y `SPRING_DATA_MONGODB_USERNAME` por el mismo usuario codificado, y `MONGO_INITDB_ROOT_PASSWORD` y `SPRING_DATA_MONGODB_PASSWORD` por la misma clave codificada. Vuelve a aplicar el manifiesto y recrea MongoDB para que la imagen oficial inicialice las credenciales nuevas:

```bash
kubectl apply -f k8s/03-db-secret.yaml
kubectl delete pod -l app=mongo -n practica-s11
kubectl rollout status deployment/mongo -n practica-s11
kubectl rollout restart deployment/backend -n practica-s11
```

MongoDB solo usa las variables `MONGO_INITDB_*` al inicializar un directorio de datos vacío. Si el PVC ya contiene una base inicializada, cambiar el Secret no cambia la contraseña guardada en esa base.

## Pruebas y evidencias

1. **Salud del namespace:**

   ```bash
   kubectl get all,pvc,configmaps,secrets -n practica-s11
   ```

2. **PVC asociado y enlazado:** el estado esperado es `Bound`; el Pod de MongoDB debe mostrar el montaje `/data/db`.

   ```bash
   kubectl describe pvc mongo-pvc -n practica-s11
   kubectl describe pod -l app=mongo -n practica-s11
   ```

3. **Inyección de configuración y credenciales:** verifica que existan las variables sin dejar la contraseña visible en la captura:

   ```bash
   POD=$(kubectl get pod -n practica-s11 -l app=backend -o jsonpath='{.items[0].metadata.name}')
   kubectl exec "$POD" -n practica-s11 -- sh -c 'env | grep -E "^(SPRING_DATA_MONGODB_|SERVER_PORT)" | sed -E "s/(PASSWORD)=.*/\1=[REDACTED]/"'
   ```

4. **Lectura/escritura reactiva desde el cliente:** averigua el NodePort asignado:

   ```bash
   kubectl get svc backend -n practica-s11
   ```

   Usa el puerto que aparece en la columna `PORT(S)` (por ejemplo, `8080:30xxx/TCP`) y reemplaza `<NODE_PORT>` y `<IP_DEL_NODO>`:

   ```bash
   curl -i -X POST http://<IP_DEL_NODO>:<NODE_PORT>/api/notes \
     -H 'Content-Type: application/json' \
     -d '{"text":"Mi primera nota persistente"}'

   curl -i http://<IP_DEL_NODO>:<NODE_PORT>/api/notes
   ```

   En Minikube, puedes consultar la URL asignada al servicio:

   ```bash
   minikube service backend -n practica-s11 --url
   ```

   Usa la URL resultante en los dos `curl`. El POST debe responder `201 Created` y devolver la nota con su `id`; el GET debe incluirla.

   Si el cliente no puede alcanzar la IP del clúster, usa un túnel local para validar la API:

   ```bash
   kubectl port-forward service/backend 8080:8080 -n practica-s11
   ```

   En otra terminal, repite los `curl` contra `http://localhost:8080`.

## Limpieza

Elimina todos los objetos del laboratorio (incluidos los datos del PVC):

```bash
kubectl delete namespace practica-s11
```
