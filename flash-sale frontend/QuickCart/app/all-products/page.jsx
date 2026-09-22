'use client'

import { useCallback, useEffect, useRef, useState } from "react";
import ProductCard from "@/components/ProductCard";
import Navbar from "@/components/Navbar";
import Footer from "@/components/Footer";
import { useAppContext } from "@/context/AppContext";
import { buildCatalogProductsPath } from "@/lib/api";

const PAGE_SIZE = 20;

const readUrlState = () => {
    if (typeof window === "undefined") {
        return { query: "", category: "", sort: "NEWEST", page: 0 };
    }
    const params = new URLSearchParams(window.location.search);
    const parsedPage = Number.parseInt(params.get("page") || "0", 10);
    const query = params.get("q") || "";
    const requestedSort = params.get("sort") || "NEWEST";
    const allowedSorts = ["NEWEST", "PRICE_ASC", "PRICE_DESC", "RELEVANCE"];
    return {
        query,
        category: params.get("categorySlug") || "",
        sort: allowedSorts.includes(requestedSort) && (requestedSort !== "RELEVANCE" || query.trim()) ? requestedSort : "NEWEST",
        page: Number.isInteger(parsedPage) && parsedPage >= 0 ? parsedPage : 0,
    };
};

const AllProducts = () => {
    const { request } = useAppContext();
    const [products, setProducts] = useState([]);
    const [categories, setCategories] = useState([]);
    const [searchInput, setSearchInput] = useState("");
    const [searchQuery, setSearchQuery] = useState("");
    const [selectedCategory, setSelectedCategory] = useState("");
    const [sort, setSort] = useState("NEWEST");
    const [page, setPage] = useState(0);
    const [pageInfo, setPageInfo] = useState({ number: 0, totalElements: 0, totalPages: 0, hasNext: false });
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [hydrated, setHydrated] = useState(false);
    const skipInitialSearchDebounce = useRef(true);

    useEffect(() => {
        const initial = readUrlState();
        setSearchInput(initial.query);
        setSearchQuery(initial.query.trim());
        setSelectedCategory(initial.category);
        setSort(["NEWEST", "PRICE_ASC", "PRICE_DESC", "RELEVANCE"].includes(initial.sort) ? initial.sort : "NEWEST");
        setPage(initial.page);
        setHydrated(true);
    }, []);

    useEffect(() => {
        request("/api/v1/catalog/categories")
            .then((response) => setCategories(response.data?.data || []))
            .catch(() => setCategories([]));
    }, [request]);

    useEffect(() => {
        if (!hydrated || skipInitialSearchDebounce.current) {
            if (hydrated) skipInitialSearchDebounce.current = false;
            return undefined;
        }
        const timer = window.setTimeout(() => {
            setSearchQuery(searchInput.trim());
            setPage(0);
        }, 350);
        return () => window.clearTimeout(timer);
    }, [hydrated, searchInput]);

    const loadProducts = useCallback(async () => {
        if (!hydrated) return;
        setLoading(true);
        setError(null);
        const requestPath = buildCatalogProductsPath({ q: searchQuery, categorySlug: selectedCategory, sort, page, size: PAGE_SIZE });
        const nextUrl = `${window.location.pathname}?${requestPath.split("?")[1]}`;
        window.history.replaceState(window.history.state, "", nextUrl);
        try {
            const response = await request(requestPath);
            setProducts(response.data?.data || []);
            setPageInfo(response.data?.page || { number: page, totalElements: 0, totalPages: 0, hasNext: false });
        } catch (requestError) {
            setProducts([]);
            setPageInfo({ number: page, totalElements: 0, totalPages: 0, hasNext: false });
            setError(requestError?.message || "Could not load the catalog.");
        } finally {
            setLoading(false);
        }
    }, [hydrated, page, request, searchQuery, selectedCategory, sort]);

    useEffect(() => {
        loadProducts();
    }, [loadProducts]);

    const selectCategory = (category) => {
        setSelectedCategory(category);
        setPage(0);
    };

    const clearFilters = () => {
        setSearchInput("");
        setSearchQuery("");
        setSelectedCategory("");
        setSort("NEWEST");
        setPage(0);
    };

    return (
        <>
            <Navbar />
            <main className="flex flex-col items-start px-6 pb-14 pt-12 md:px-16 lg:px-32">
                <div className="flex w-full flex-col gap-5 md:flex-row md:items-end md:justify-between">
                    <div>
                        <p className="text-2xl font-medium">All products</p>
                        <div className="mt-1 h-0.5 w-16 rounded-full bg-orange-600" />
                        <p className="mt-3 text-sm text-gray-500">Search the catalog, choose a category, and sort the results.</p>
                    </div>
                    <label className="w-full md:max-w-sm">
                        <span className="sr-only">Search products</span>
                        <input
                            value={searchInput}
                            onChange={(event) => setSearchInput(event.target.value)}
                            placeholder="Search products or SKU"
                            className="w-full rounded-full border border-gray-200 px-5 py-3 text-sm outline-none transition focus:border-orange-500 focus:ring-2 focus:ring-orange-100"
                        />
                    </label>
                </div>

                <div className="mt-7 flex w-full flex-col gap-4 rounded-2xl border border-gray-100 bg-white p-4 shadow-sm md:flex-row md:items-center md:justify-between">
                    <div className="flex flex-wrap gap-2">
                        <button type="button" onClick={() => selectCategory("")} className={`rounded-full border px-4 py-2 text-sm transition ${!selectedCategory ? "border-orange-600 bg-orange-50 text-orange-600" : "border-gray-200 text-gray-600 hover:border-orange-300"}`}>All</button>
                        {categories.map((category) => (
                            <button type="button" key={category.id} onClick={() => selectCategory(category.slug)} className={`rounded-full border px-4 py-2 text-sm transition ${selectedCategory === category.slug ? "border-orange-600 bg-orange-50 text-orange-600" : "border-gray-200 text-gray-600 hover:border-orange-300"}`}>
                                {category.name}
                            </button>
                        ))}
                    </div>
                    <label className="flex items-center gap-2 text-sm text-gray-500">
                        Sort by
                        <select value={sort} onChange={(event) => { setSort(event.target.value); setPage(0); }} className="rounded-full border border-gray-200 bg-white px-4 py-2 text-sm text-gray-700 outline-none focus:border-orange-500">
                            <option value="NEWEST">Newest</option>
                            <option value="PRICE_ASC">Price: low to high</option>
                            <option value="PRICE_DESC">Price: high to low</option>
                            {searchQuery && <option value="RELEVANCE">Relevance</option>}
                        </select>
                    </label>
                </div>

                <div className="mt-6 flex w-full items-center justify-between text-sm text-gray-500">
                    <span>{loading ? "Loading catalog…" : `${pageInfo.totalElements || 0} products`}</span>
                    {(searchQuery || selectedCategory || sort !== "NEWEST") && <button type="button" onClick={clearFilters} className="font-medium text-orange-600 hover:text-orange-700">Clear filters</button>}
                </div>

                {error && <div role="alert" className="mt-4 w-full rounded-xl border border-red-100 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div>}

                {loading ? (
                    <div className="mt-8 grid w-full grid-cols-2 gap-6 md:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5">
                        {Array.from({ length: 10 }).map((_, index) => <div key={index} className="h-72 animate-pulse rounded-xl bg-gray-100" />)}
                    </div>
                ) : products.length > 0 ? (
                    <div className="mt-8 grid w-full grid-cols-2 gap-6 md:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5">
                        {products.map((product) => <ProductCard key={product.id} product={product} />)}
                    </div>
                ) : (
                    <div className="mt-8 w-full rounded-2xl border border-dashed border-gray-200 px-6 py-16 text-center">
                        <p className="text-lg font-medium text-gray-700">No products found</p>
                        <p className="mt-2 text-sm text-gray-500">Try another search or clear your filters.</p>
                        <button type="button" onClick={clearFilters} className="mt-5 rounded-full bg-orange-600 px-5 py-2.5 text-sm font-medium text-white transition hover:bg-orange-700">View all products</button>
                    </div>
                )}

                {(pageInfo.totalPages > 1 || page > 0) && <div className="mt-10 flex w-full items-center justify-center gap-3">
                    <button type="button" disabled={page <= 0} onClick={() => setPage((current) => Math.max(0, current - 1))} className="rounded-full border border-gray-200 px-5 py-2 text-sm text-gray-600 disabled:cursor-not-allowed disabled:opacity-40">Previous</button>
                    <span className="text-sm text-gray-500">Page {page + 1} of {Math.max(pageInfo.totalPages, page + 1)}</span>
                    <button type="button" disabled={!pageInfo.hasNext} onClick={() => setPage((current) => current + 1)} className="rounded-full border border-gray-200 px-5 py-2 text-sm text-gray-600 disabled:cursor-not-allowed disabled:opacity-40">Next</button>
                </div>}
            </main>
            <Footer />
        </>
    );
};

export default AllProducts;
