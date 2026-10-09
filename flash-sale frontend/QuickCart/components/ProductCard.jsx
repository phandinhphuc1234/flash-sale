import React from 'react'
import { useAppContext } from '@/context/AppContext';
import { formatVnd } from '@/lib/api';
import { selectDisplayPrice } from '@/lib/purchasePresentation.mjs';

const ProductCard = ({ product }) => {

    const { router } = useAppContext()

    return (
        <div
            onClick={() => { router.push('/product/' + product.slug); scrollTo(0, 0) }}
            className="flex flex-col items-start gap-0.5 max-w-[200px] w-full cursor-pointer"
        >
            <div className="cursor-pointer group relative bg-gray-500/10 rounded-lg w-full h-52 flex items-center justify-center">
                {product.media?.[0]?.url ? <img src={product.media[0].url} alt={product.media[0].altText || product.name} className="group-hover:scale-105 transition object-cover w-full h-full" /> : <span className="text-sm text-gray-400">No image</span>}
            </div>

            <p className="md:text-base font-medium pt-2 w-full truncate">{product.name}</p>
            <p className="w-full text-xs text-gray-500/70 max-sm:hidden truncate">{product.shortDescription}</p>

            <div className="flex items-end justify-between w-full mt-1">
                <p className="text-base font-medium">{(() => { const price = selectDisplayPrice(product.variants); return price ? `${price.prefix ? `${price.prefix} ` : ''}${formatVnd(price.amount)}` : 'Price unavailable'; })()}</p>
                <button onClick={(event) => { event.stopPropagation(); router.push('/product/' + product.slug); }} className="max-sm:hidden px-4 py-1.5 text-gray-500 border border-gray-500/20 rounded-full text-xs hover:bg-slate-50 transition">
                    Buy now
                </button>
            </div>
        </div>
    )
}

export default ProductCard
