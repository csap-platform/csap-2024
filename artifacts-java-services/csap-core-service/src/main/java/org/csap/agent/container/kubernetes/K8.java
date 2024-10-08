package org.csap.agent.container.kubernetes;

import java.util.Arrays ;
import java.util.List ;
import java.util.stream.Collectors ;

public enum K8 {

	//
	// Used to verify api connectivity
	//
	heartbeat( "heartbeat" ),

	clusterNamespace( "kubernetes-namespace" ),
	settings( "kubernetes-settings" ), namespace( "namespace" ), replicaCount( "replica-count" ),

	masters( "masters" ), masterDns( "master-dns" ), kubernetesMasterHostNames( "kubernetes-masters" ),

	serviceType( "service-type" ),
	None( "none" ), CLUSTER_IP( "ClusterIP" ),
	LoadBalancer( "LoadBalancer" ), ExternalName( "ExternalName" ),

	NODE_PORT( "NodePort" ), containerPort( "containerPort" ), servicePort( "servicePort" ),
	hostPort( "hostPort" ), portName( "name" ), protocol( "protocol" ),

	ingressPath( "ingress-path" ), ingressPort( "ingress-port" ), ingressHost( "ingress-host" ),
	
	ingressControllerPodLabelSelector( "app.kubernetes.io/component=controller,app.kubernetes.io/instance=ingress-nginx" ), 
	ingressService( "ingress-nginx-controller" ), ingressNamespace( "ingress-nginx" ),
	
	//  app.kubernetes.io/instance=ingress-nginx
	
	ingressAnnotations( "ingress-annotations" ),

	systemNamespace( "kube-system" ),
	metricsServerLabel( "app.kubernetes.io/name=metrics-server" ),

	arguments( "command-arguments" ),

	nodeSelectors( "node-selectors" ),
	podAnnotations( "pod-annotations" ),

	labelsByType( "labelsByType" ),
	readinessProbe( "readinessProbe" ),
	livenessProbe( "livenessProbe" ),

	resources( "resources" ),

	// storage
	storageName( "name" ), storageReadOnly( "readOnly" ), storageType( "type" ), storagePath( "path" ),
	mountPath( "mountPath" ), hostPath( "hostPath" ),
	emptyDir( "emptyDir" ), sizeLimit( "sizeLimit" ),
	persistentVolumeClaim( "persistentVolumeClaim" ), claimName( "claimName" ), storageClass( "storageClass" ),
	createIfNotPresent( "createIfNotPresent" ),
	storage( "storage" ), accessModes( "accessModes" ), selectorMatchLabels( "selectorMatchLabels" ),

	addCsapTools( "add-csap-tools" ),

	//
	// apiPath for kubectl describe
	//
	apiPath( "apiPath" ),

	//
	// reports
	//
	report_namespace_all( "namespace-all" ), report_metrics( "csap-metrics-only" ), report_events( "recent-events" ),

	//
	// Node Usage Reports
	//
	memoryGb( "memoryGb" ), // cores("cores"),

	//
	// namespace monitors enable double counting of metrics
	//
	namespaceMonitor( "namespaceMonitor" ),

	//
	// Metrics Server
	//
	nodes( "nodes" ), pods( "pods" ), podsRunning( "podsRunning" ), podsNotRunning( "podsNotRunning" ), containers(
			"containers" ), containerCount(
					"containerCount" ), cores( "cores" ), memoryInMb( "memoryInMb" ), formatCpu( "cpu" );

	private String jsonToken ;

	public String val ( ) {

		return jsonToken ;

	}

	private K8 ( String jsonToken ) {

		this.jsonToken = jsonToken ;

	}

	// definition path
	public String spath ( ) {

		return "/" + K8.settings.val( ) + "/" + val( ) ;

	}

	static public List<String> k8TypeList ( ) {

		return Arrays.asList( new K8[] {
				None, CLUSTER_IP, NODE_PORT, LoadBalancer, ExternalName
		} )
				.stream( )
				.map( K8::val )
				.collect( Collectors.toList( ) ) ;

	}
}
