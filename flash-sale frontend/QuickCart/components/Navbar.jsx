"use client"
import React from "react";
import { CartIcon } from "@/assets/assets";
import Link from "next/link"
import { useAppContext } from "@/context/AppContext";
import AccountMenu from "@/components/account/AccountMenu";

const Navbar = () => {

  const { router, getCartCount } = useAppContext();
  const cartCount = getCartCount();

  return (
    <nav className="flex items-center justify-between px-6 md:px-16 lg:px-32 py-3 border-b border-gray-300 text-gray-700">
      <button onClick={() => router.push('/')} className="text-xl font-medium tracking-tight text-gray-800">Ecommerce<span className="text-orange-600">.</span></button>
      <div className="flex items-center gap-4 lg:gap-8 max-md:hidden">
        <Link href="/" className="hover:text-gray-900 transition">
          Home
        </Link>
        <Link href="/all-products" className="hover:text-gray-900 transition">
          Shop
        </Link>
        <Link href="/" className="hover:text-gray-900 transition">
          Flash Sale
        </Link>
        <Link href="/" className="hover:text-gray-900 transition">
          Help
        </Link>


      </div>

      <ul className="hidden md:flex items-center gap-4 ">
        <button aria-label={`Cart with ${cartCount} items`} onClick={() => router.push('/cart')} className="group relative flex items-center gap-2 rounded-full px-3 py-2 text-sm transition hover:bg-orange-50 hover:text-orange-700">
          <CartIcon />
          <span>Cart</span>
          <span key={cartCount} className="flex min-w-5 items-center justify-center rounded-full bg-orange-600 px-1.5 py-0.5 text-[11px] font-medium text-white transition-transform group-hover:scale-110">{cartCount}</span>
        </button>
        <AccountMenu />
      </ul>

      <div className="flex items-center md:hidden gap-3">
        <button aria-label={`Cart with ${cartCount} items`} onClick={() => router.push('/cart')} className="relative rounded-full p-2 transition hover:bg-orange-50">
          <CartIcon />
          {cartCount > 0 && <span className="absolute -right-1 -top-1 flex h-5 min-w-5 items-center justify-center rounded-full bg-orange-600 px-1 text-[10px] text-white">{cartCount}</span>}
        </button>
        <AccountMenu compact />
      </div>
    </nav>
  );
};

export default Navbar;
