package com.watube.yard.extensions

import androidx.lifecycle.MutableLiveData

// region Common Utilities
fun <T> MutableLiveData<T>.updateIfChanged(newValue: T) {
    if (value != newValue) value = newValue
}
// endregion
