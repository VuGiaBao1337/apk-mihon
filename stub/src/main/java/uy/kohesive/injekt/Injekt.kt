package uy.kohesive.injekt

inline fun <reified T : Any> injectLazy(): Lazy<T> = lazy {
    throw UnsupportedOperationException("Injekt stub — not available at compile time")
}
