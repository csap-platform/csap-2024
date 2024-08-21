package org.csap.security;

import java.util.HashSet ;
import java.util.List ;
import java.util.Map ;
import java.util.Set ;

import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;
import org.springframework.ldap.core.ContextSource ;
import org.springframework.ldap.core.DirContextOperations ;
import org.springframework.security.core.GrantedAuthority ;
import org.springframework.security.core.authority.SimpleGrantedAuthority ;
import org.springframework.security.ldap.userdetails.DefaultLdapAuthoritiesPopulator ;

public class CsapAuthoritiesLdapPopulator extends DefaultLdapAuthoritiesPopulator {

	final Logger logger = LoggerFactory.getLogger( this.getClass( ) ) ;

	Map<String, List<String>> additionalUserRoles ;

	public CsapAuthoritiesLdapPopulator ( Map<String, List<String>> additionalUserRoles, ContextSource contextSource,
			String groupSearchBase ) {

		super( contextSource, groupSearchBase ) ;

		this.additionalUserRoles = additionalUserRoles ;

	}

	protected Set<GrantedAuthority> getAdditionalRoles (
															DirContextOperations user ,
															String username ) {

		logger.debug( "Checking '{}' for additional roles: '{}'", username, additionalUserRoles ) ;

		if ( additionalUserRoles != null && additionalUserRoles.containsKey( username ) ) {

			var authorities = new HashSet<GrantedAuthority>( ) ;

			additionalUserRoles.get( username ).stream( ).forEach( additionalRole -> {

				var cleanedUpRoleName = getRolePrefix( ) + additionalRole.replaceAll( "cn=", "" ) ;

				authorities.add( new SimpleGrantedAuthority( cleanedUpRoleName ) ) ;

			} ) ;

			logger.info( "Added Roles for '{}' : {}", username, authorities ) ;

			return authorities ;

		} else {

			return null ;

		}

	}

//	public Set<GrantedAuthority> getGroupMembershipRoles(String userDn, String username) {
//
//		var authoritiesBase = super.getGroupMembershipRoles( userDn, username ) ;
//		
//		var authorities = new HashSet<GrantedAuthority>( ) ;
//		
//		for ( var authBase : authoritiesBase) ; {
//			authorities.add(authorityMapper.apply(role));
//		}
//		
////		Set<GrantedAuthority> authorities = new HashSet<>();
////		logger.trace(LogMessage.of(() -> "Searching for roles for user " + username + " with DN " + userDn
////				+ " and filter " + this.groupSearchFilter + " in search base " + getGroupSearchBase()));
////		Set<Map<String, List<String>>> userRoles = getLdapTemplate().searchForMultipleAttributeValues(
////				getGroupSearchBase(), this.groupSearchFilter, new String[] { userDn, username },
////				new String[] { this.groupRoleAttribute });
////		logger.debug(LogMessage.of(() -> "Found roles from search " + userRoles));
////		for (Map<String, List<String>> role : userRoles) {
////			authorities.add(this.authorityMapper.apply(role));
////		}
//		return authorities;
//	}

//	public Set<GrantedAuthority> getGroupMembershipRoles(String userDn, String username) {
////		if (getGroupSearchBase() == null) {
////			return new HashSet<>();
////		}
////		Set<GrantedAuthority> authorities = new HashSet<>();
////		logger.trace(LogMessage.of(() -> "Searching for roles for user " + username + " with DN " + userDn
////				+ " and filter " + this.groupSearchFilter + " in search base " + getGroupSearchBase()));
////		Set<Map<String, List<String>>> userRoles = getLdapTemplate().searchForMultipleAttributeValues(
////				getGroupSearchBase(), this.groupSearchFilter, new String[] { userDn, username },
////				new String[] { this.groupRoleAttribute });
////		logger.debug(LogMessage.of(() -> "Found roles from search " + userRoles));
////		for (Map<String, List<String>> role : userRoles) {
////			authorities.add(this.authorityMapper.apply(role));
////		}
//		return super.getGroupMembershipRoles( userDn.toLowerCase( ), username );
//	}

}
