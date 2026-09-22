'use client'
import Navbar from '@/components/seller/Navbar'
import Sidebar from '@/components/seller/Sidebar'
import React, { useEffect } from 'react'
import { useAppContext } from '@/context/AppContext'

const Layout = ({ children }) => {
  const { authReady, isAdmin, router, userData } = useAppContext()

  useEffect(() => {
    if (authReady && (!userData || !isAdmin)) router.replace('/login?next=/seller')
  }, [authReady, isAdmin, router, userData])

  if (!authReady || !userData || !isAdmin) {
    return <main className="flex min-h-screen items-center justify-center bg-[#f8fafc] text-sm text-slate-500">Checking admin access…</main>
  }

  return (
    <div className='min-h-screen bg-[#f8fafc] text-slate-900'>
      <Navbar />
      <div className='flex w-full'>
        <Sidebar />
        {children}
      </div>
    </div>
  )
}

export default Layout
