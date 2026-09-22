import React from 'react'
import { useAppContext } from '@/context/AppContext'

const Navbar = () => {

  const { logout, router, userData } = useAppContext()

  return (
    <header className='flex items-center justify-between border-b border-slate-200 bg-white px-4 py-3 text-slate-700 md:px-8'>
      <div className='flex items-center gap-4'>
        <button onClick={()=>router.push('/')} className='text-xl font-medium tracking-tight text-slate-800'>Ecommerce<span className='text-orange-600'>.</span></button>
        <span className='hidden border-l border-slate-200 pl-4 text-xs uppercase tracking-[0.2em] text-slate-400 sm:inline'>Operations console</span>
      </div>
      <div className='flex items-center gap-3'>
        <div className='hidden text-right sm:block'><p className='text-xs text-slate-400'>Signed in as</p><p className='max-w-48 truncate text-sm font-medium text-slate-800'>{userData?.login || 'admin'}</p></div>
        <span className='rounded-full bg-orange-50 px-3 py-1 text-xs font-semibold text-orange-700 ring-1 ring-orange-100'>ROLE_ADMIN</span>
        <button onClick={()=>router.push('/')} className='rounded-xl border border-slate-200 px-3 py-2 text-xs font-medium text-slate-600 transition hover:border-orange-200 hover:bg-orange-50 hover:text-orange-700'>Storefront</button>
        <button onClick={logout} className='rounded-xl border border-red-100 px-3 py-2 text-xs font-medium text-red-600 transition hover:bg-red-50'>Sign out</button>
      </div>
    </header>
  )
}

export default Navbar
