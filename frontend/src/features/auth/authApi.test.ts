import { describe, it, expect } from 'vitest';

import { authApi } from './authApi';

describe('authApi', () => {
  describe('API configuration', () => {
    it('should have correct reducerPath', () => {
      expect(authApi.reducerPath).toBe('authApi');
    });
  });

  describe('login mutation', () => {
    it('should have correct endpoint configuration', () => {
      const endpoint = authApi.endpoints.login;
      
      expect(endpoint).toBeDefined();
      expect(endpoint.name).toBe('login');
    });

    it('should be configured as a mutation', () => {
      const endpoint = authApi.endpoints.login;
      
      // RTK Query mutations have a 'initiate' method
      expect(typeof endpoint.initiate).toBe('function');
    });
  });

  describe('logout mutation', () => {
    it('should have correct endpoint configuration', () => {
      const endpoint = authApi.endpoints.logout;
      
      expect(endpoint).toBeDefined();
      expect(endpoint.name).toBe('logout');
    });

    it('should be configured as a mutation', () => {
      const endpoint = authApi.endpoints.logout;
      
      expect(typeof endpoint.initiate).toBe('function');
    });
  });

  describe('refreshToken mutation', () => {
    it('should have correct endpoint configuration', () => {
      const endpoint = authApi.endpoints.refreshToken;
      
      expect(endpoint).toBeDefined();
      expect(endpoint.name).toBe('refreshToken');
    });

    it('should be configured as a mutation', () => {
      const endpoint = authApi.endpoints.refreshToken;
      
      expect(typeof endpoint.initiate).toBe('function');
    });
  });

  describe('getMe query', () => {
    it('should have correct endpoint configuration', () => {
      const endpoint = authApi.endpoints.getMe;
      
      expect(endpoint).toBeDefined();
      expect(endpoint.name).toBe('getMe');
    });

    it('should be configured as a query', () => {
      const endpoint = authApi.endpoints.getMe;
      
      // RTK Query queries have an 'initiate' method
      expect(typeof endpoint.initiate).toBe('function');
    });
  });

  describe('exported hooks', () => {
    it('should export useLoginMutation hook', () => {
      expect(authApi.useLoginMutation).toBeDefined();
      expect(typeof authApi.useLoginMutation).toBe('function');
    });

    it('should export useLogoutMutation hook', () => {
      expect(authApi.useLogoutMutation).toBeDefined();
      expect(typeof authApi.useLogoutMutation).toBe('function');
    });

    it('should export useRefreshTokenMutation hook', () => {
      expect(authApi.useRefreshTokenMutation).toBeDefined();
      expect(typeof authApi.useRefreshTokenMutation).toBe('function');
    });

    it('should export useGetMeQuery hook', () => {
      expect(authApi.useGetMeQuery).toBeDefined();
      expect(typeof authApi.useGetMeQuery).toBe('function');
    });
  });

  describe('endpoint types', () => {
    it('should have all expected endpoints', () => {
      const endpointNames = Object.keys(authApi.endpoints);
      
      expect(endpointNames).toContain('login');
      expect(endpointNames).toContain('logout');
      expect(endpointNames).toContain('refreshToken');
      expect(endpointNames).toContain('getMe');
    });

    it('should have exactly 4 endpoints', () => {
      const endpointNames = Object.keys(authApi.endpoints);
      
      expect(endpointNames).toHaveLength(4);
    });
  });
});
