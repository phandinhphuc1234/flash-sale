import React from 'react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';

const SideBar = () => {
    const pathname = usePathname()
    const menuItems = [
        { name: 'Overview', path: '/seller', icon: '⌂' },
        { name: 'Add product', path: '/seller/products/new', icon: '+' },
        { name: 'Catalog', path: '/seller/product-list', icon: '▦' },
        { name: 'Inventory', path: '/seller/inventory', icon: '□' },
        { name: 'Campaigns', path: '/seller/campaigns', icon: '◇' },
    ];

    return (
        <aside className='min-h-[calc(100vh-65px)] w-20 shrink-0 border-r border-slate-200 bg-white py-5 text-sm md:w-64'>
            <div className='px-3 pb-4 md:px-5'><p className='hidden text-xs font-semibold uppercase tracking-[0.18em] text-slate-400 md:block'>Workspace</p><p className='mt-1 hidden text-xs text-slate-500 md:block'>Manage the flash-sale storefront</p></div>
            {menuItems.map((item) => {

                const isActive = pathname === item.path;

                return (
                    <Link href={item.path} key={item.name} passHref>
                        <div className={`mx-2 flex items-center gap-3 rounded-xl px-3 py-3 transition md:mx-3 md:px-4 ${isActive ? 'bg-orange-50 text-orange-700 shadow-sm ring-1 ring-orange-100' : 'text-slate-600 hover:bg-orange-50/60 hover:text-slate-950'}`}>
                            <span className={`flex h-8 w-8 shrink-0 items-center justify-center rounded-lg text-lg ${isActive ? 'bg-orange-500 text-white' : 'bg-slate-100 text-slate-500'}`}>{item.icon}</span>
                            <p className='hidden font-medium md:block'>{item.name}</p>
                        </div>
                    </Link>
                );
            })}
        </aside>
    );
};

export default SideBar;
