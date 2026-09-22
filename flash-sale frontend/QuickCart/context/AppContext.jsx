"use client";

import { apiFetch, ApiError } from "@/lib/api";
import { getAuthorities, isAdminToken, readAccessTokenClaims } from "@/lib/authClaims";
import { normalizeAccountProfile } from "@/lib/accountProfile";
import { useRouter } from "next/navigation";
import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from "react";

export const EMPTY_CART = {
  items: [],
  distinctItemCount: 0,
  totalQuantity: 0,
  cartVersion: 0,
  updatedAt: null,
};

export const AppContext = createContext(null);

export const useAppContext = () => useContext(AppContext);

export function AppContextProvider({ children }) {
  const currency = process.env.NEXT_PUBLIC_CURRENCY || "₫";
  const router = useRouter();
  const [products, setProducts] = useState([]);
  const [userData, setUserData] = useState(null);
  const [accountProfile, setAccountProfile] = useState(null);
  const [isSeller, setIsSeller] = useState(false);
  const [accessToken, setAccessToken] = useState(null);
  const [authReady, setAuthReady] = useState(false);
  const [cart, setCart] = useState(EMPTY_CART);
  const [cartLoading, setCartLoading] = useState(false);
  const [cartError, setCartError] = useState(null);
  const refreshPromiseRef = useRef(null);

  const fetchProductData = useCallback(async () => {
    const response = await apiFetch("/api/v1/catalog/products?page=0&size=40");
    setProducts(response.data?.data || []);
  }, []);

  const fetchCartWithToken = useCallback(async (token) => {
    if (!token) {
      setCart(EMPTY_CART);
      return EMPTY_CART;
    }
    setCartLoading(true);
    setCartError(null);
    try {
      const response = await apiFetch("/api/v1/cart", {}, token);
      const nextCart = response?.data || EMPTY_CART;
      setCart(nextCart);
      return nextCart;
    } catch (error) {
      setCartError(error);
      throw error;
    } finally {
      setCartLoading(false);
    }
  }, []);

  const fetchAccountProfileWithToken = useCallback(async (token) => {
    if (!token) {
      setAccountProfile(null);
      return null;
    }
    try {
      const response = await apiFetch("/api/v1/auth/me", {}, token);
      const profile = normalizeAccountProfile(response?.data);
      if (!profile) throw new ApiError(502, { errorCode: "ACCOUNT_PROFILE_INVALID", message: "Account profile is unavailable." });
      setAccountProfile(profile);
      return profile;
    } catch (error) {
      setAccountProfile(null);
      throw error;
    }
  }, []);

  const refreshAccessToken = useCallback(async () => {
    if (refreshPromiseRef.current) return refreshPromiseRef.current;
    refreshPromiseRef.current = apiFetch("/api/v1/auth/refresh", { method: "POST" })
      .then(async (response) => {
        const token = response.data.accessToken;
        const claims = readAccessTokenClaims(token);
        setAccessToken(token);
        setUserData({ authenticated: true, login: claims.sub, authorities: getAuthorities(token) });
        try {
          await fetchAccountProfileWithToken(token);
        } catch {
          // Profile is additive; keep the authenticated session with a safe fallback.
        }
        try {
          await fetchCartWithToken(token);
        } catch {
          // Authentication succeeded. Keep the session even when Cart is
          // temporarily unavailable or its browser preflight is misconfigured.
        }
        return token;
      })
      .catch(() => {
        setAccessToken(null);
        setUserData(null);
        setCart(EMPTY_CART);
        return null;
      })
      .finally(() => {
        refreshPromiseRef.current = null;
      });
    return refreshPromiseRef.current;
  }, [fetchAccountProfileWithToken, fetchCartWithToken]);

  const request = useCallback(async (path, init = {}, retried = false) => {
    try {
      return await apiFetch(path, init, accessToken);
    } catch (error) {
      if (error instanceof ApiError && error.status === 401 && !retried) {
        const token = await refreshAccessToken();
        if (token) return apiFetch(path, init, token);
      }
      throw error;
    }
  }, [accessToken, refreshAccessToken]);

  const loadCart = useCallback(async (tokenOverride) => {
    const token = tokenOverride || accessToken;
    if (!token || !userData) {
      setCart(EMPTY_CART);
      return EMPTY_CART;
    }
    return fetchCartWithToken(token);
  }, [accessToken, fetchCartWithToken, userData]);

  const setCartItem = useCallback(async (variantId, quantity) => {
    if (!Number.isInteger(quantity) || quantity < 1 || quantity > 10) {
      throw new ApiError(400, {
        errorCode: "CART_VALIDATION_ERROR",
        message: "Quantity must be a whole number from 1 to 10.",
      });
    }
    setCartError(null);
    await request(`/api/v1/cart/items/${variantId}`, {
      method: "PUT",
      body: JSON.stringify({ quantity }),
    });
    return loadCart();
  }, [loadCart, request]);

  const removeCartItem = useCallback(async (variantId) => {
    setCartError(null);
    await request(`/api/v1/cart/items/${variantId}`, { method: "DELETE" });
    // The backend increments cartVersion for every mutation. Reload instead of
    // applying an optimistic local projection so the next cart checkout sends
    // the current snapshot revision and cannot fail with CART_CHANGED.
    return loadCart();
  }, [loadCart, request]);

  const clearCart = useCallback(async () => {
    setCartError(null);
    await request("/api/v1/cart", { method: "DELETE" });
    setCart(EMPTY_CART);
  }, [request]);

  const login = useCallback(async (payload) => {
    const response = await apiFetch("/api/v1/auth/login", {
      method: "POST",
      body: JSON.stringify(payload),
    });
    const token = response.data.accessToken;
    const claims = readAccessTokenClaims(token);
    setAccessToken(token);
    setUserData({ authenticated: true, login: payload.login || claims.sub, authorities: getAuthorities(token) });
    try {
      await fetchAccountProfileWithToken(token);
    } catch {
      // A temporary profile read failure must not turn a successful login into a failure.
    }
    try {
      await fetchCartWithToken(token);
    } catch {
      // Cart has its own availability boundary and must not turn a successful
      // authentication response into a misleading login failure.
    }
  }, [fetchAccountProfileWithToken, fetchCartWithToken]);

  const register = useCallback((payload) => apiFetch("/api/v1/auth/register", {
    method: "POST",
    body: JSON.stringify(payload),
  }), []);

  const updateAccountProfile = useCallback(async (payload) => {
    const response = await request("/api/v1/auth/me/profile", {
      method: "PATCH",
      body: JSON.stringify(payload),
    });
    const profile = normalizeAccountProfile(response?.data);
    if (!profile) throw new ApiError(502, { errorCode: "ACCOUNT_PROFILE_INVALID", message: "Account profile is unavailable." });
    setAccountProfile(profile);
    return profile;
  }, [request]);

  const resetSession = useCallback(() => {
    setAccessToken(null);
    setUserData(null);
    setAccountProfile(null);
    setCart(EMPTY_CART);
    setCartError(null);
  }, []);

  const logout = useCallback(async () => {
    try {
      await apiFetch("/api/v1/auth/logout", { method: "POST" }, accessToken);
    } finally {
      resetSession();
    }
  }, [accessToken, resetSession]);

  const logoutAll = useCallback(async () => {
    try {
      await request("/api/v1/auth/logout-all", { method: "POST" });
    } finally {
      resetSession();
    }
  }, [request, resetSession]);

  useEffect(() => {
    fetchProductData().catch(() => setProducts([]));
    refreshAccessToken().finally(() => setAuthReady(true));
  }, [fetchProductData, refreshAccessToken]);

  const getCartCount = useCallback(() => cart.totalQuantity || 0, [cart.totalQuantity]);
  const getCartAmount = useCallback(() => cart.items.reduce((sum, item) => {
    if (!item.detailsAvailable || item.sellable !== true || item.basePrice == null) return sum;
    return sum + Number(item.basePrice) * item.quantity;
  }, 0), [cart.items]);
  const isAdmin = isAdminToken(accessToken);

  const value = useMemo(() => ({
    currency,
    router,
    accessToken,
    authReady,
    request,
    login,
    register,
    updateAccountProfile,
    logout,
    logoutAll,
    isSeller,
    isAdmin,
    setIsSeller,
    userData,
    accountProfile,
    products,
    fetchProductData,
    cart,
    cartLoading,
    cartError,
    loadCart,
    setCartItem,
    removeCartItem,
    clearCart,
    getCartCount,
    getCartAmount,
  }), [accessToken, accountProfile, authReady, cart, cartError, cartLoading, clearCart, fetchProductData, getCartAmount, getCartCount, isAdmin, isSeller, loadCart, login, logout, logoutAll, products, register, removeCartItem, request, router, setCartItem, updateAccountProfile, userData]);

  return <AppContext.Provider value={value}>{children}</AppContext.Provider>;
}
