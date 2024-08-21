


kubectl apply -f nginx.yaml

kubectl describe deployment demo-xxx-nginx-deployment

kubectl expose deployment demo-xxx-nginx-deployment --port=80 --type=LoadBalancer

# undo the above