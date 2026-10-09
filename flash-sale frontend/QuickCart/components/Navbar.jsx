"use client"
import React from "react";
import { CartIcon } from "@/assets/assets";
import Link from "next/link"
import { usePathname } from "next/navigation";
import { useAppContext } from "@/context/AppContext";
import AccountMenu from "@/components/account/AccountMenu";
import {
  STOREFRONT_DESTINATIONS,
  classifyStorefrontRoute,
} from "@/lib/storefrontNavigation.mjs";

const primaryDestinations = [
  { key: STOREFRONT_DESTINATIONS.HOME, label: "Home", href: "/" },
  { key: STOREFRONT_DESTINATIONS.SHOP, label: "Shop", href: "/all-products" },
  { key: STOREFRONT_DESTINATIONS.FLASH_SALE, label: "Flash Sale", href: "/flash-sale" },
];

const navigationLinkClass = (active, compact = false) => [
  "group relative inline-flex items-center justify-center whitespace-nowrap rounded-xl font-medium",
  "transition-colors duration-200 motion-reduce:transition-none",
  "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-orange-500 focus-visible:ring-offset-2",
  compact ? "min-w-24 px-4 py-2 text-sm" : "px-3 py-2 text-sm lg:text-base",
  active
    ? "bg-orange-50 font-semibold text-orange-700"
    : "text-slate-600 hover:bg-orange-50/60 hover:text-slate-950",
].join(" ");

const indicatorClass = (active) => [
  "pointer-events-none absolute inset-x-3 -bottom-px h-0.5 origin-center rounded-full bg-orange-500",
  "transition-transform duration-200 motion-reduce:transition-none",
  active
    ? "scale-x-100"
    : "scale-x-0 group-hover:scale-x-75 group-focus-visible:scale-x-75",
].join(" ");

function StorefrontLink({ destination, activeDestination, compact = false }) {
  const active = activeDestination === destination.key;

  return (
    <Link
      href={destination.href}
      aria-current={active ? "page" : undefined}
      className={navigationLinkClass(active, compact)}
    >
      <span>{destination.label}</span>
      <span aria-hidden="true" className={indicatorClass(active)} />
    </Link>
  );
}

const Navbar = () => {

  const { getCartCount } = useAppContext();
  const pathname = usePathname();
  const activeDestination = classifyStorefrontRoute(pathname);
  const cartCount = getCartCount();
  const cartActive = activeDestination === STOREFRONT_DESTINATIONS.CART;

  return (
    <header className="border-b border-slate-200 bg-white text-slate-700">
      <nav aria-label="Storefront navigation">
        <div className="flex items-center justify-between px-6 py-3 md:px-16 lg:px-32">
          <Link
            href="/"
            aria-label="Ecommerce home"
            className="rounded-lg text-xl font-medium tracking-tight text-slate-800 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-orange-500 focus-visible:ring-offset-2"
          >
            Ecommerce<span className="text-orange-600">.</span>
          </Link>

          <div className="hidden items-center gap-2 md:flex lg:gap-4">
            {primaryDestinations.map((destination) => (
              <StorefrontLink
                key={destination.key}
                destination={destination}
                activeDestination={activeDestination}
              />
            ))}
          </div>

          <div className="hidden items-center gap-3 md:flex">
            <Link
              href="/cart"
              aria-label={`Cart with ${cartCount} items`}
              aria-current={cartActive ? "page" : undefined}
              className={`${navigationLinkClass(cartActive)} gap-2`}
            >
              <CartIcon />
              <span>Cart</span>
              <span
                key={cartCount}
                className="flex min-w-5 items-center justify-center rounded-full bg-orange-600 px-1.5 py-0.5 text-[11px] font-medium text-white transition-transform group-hover:scale-110 motion-reduce:transition-none"
              >
                {cartCount}
              </span>
              <span aria-hidden="true" className={indicatorClass(cartActive)} />
            </Link>
            <AccountMenu />
          </div>

          <div className="flex items-center gap-3 md:hidden">
            <Link
              href="/cart"
              aria-label={`Cart with ${cartCount} items`}
              aria-current={cartActive ? "page" : undefined}
              className={`relative rounded-full p-2 transition-colors motion-reduce:transition-none focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-orange-500 focus-visible:ring-offset-2 ${
                cartActive ? "bg-orange-50 text-orange-700 ring-1 ring-orange-100" : "hover:bg-orange-50"
              }`}
            >
              <CartIcon />
              {cartCount > 0 && (
                <span className="absolute -right-1 -top-1 flex h-5 min-w-5 items-center justify-center rounded-full bg-orange-600 px-1 text-[10px] text-white">
                  {cartCount}
                </span>
              )}
            </Link>
            <AccountMenu compact />
          </div>
        </div>

        <div className="flex items-center justify-center gap-2 border-t border-slate-100 px-4 py-2 md:hidden">
          {primaryDestinations.map((destination) => (
            <StorefrontLink
              key={destination.key}
              destination={destination}
              activeDestination={activeDestination}
              compact
            />
          ))}
        </div>
      </nav>
    </header>
  );
};

export default Navbar;
