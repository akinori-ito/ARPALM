# Kotlin implementation of ARPA-style n-gram language model

by Akinori Ito, 2019/7/28

## Example
```kotlin
val lm = ARPALM("file.arpa") # compressed file not allowed
val words = arrayOf("give","me","love")
val logprob = lm.getBOlprob(words)
```

##Usage

### Constructor
```kotlin
ARPALM(filename: String)
```

### Get log probability
```kotlin
getBOlprob(words: Array<String>):Double
getBOlprob(ids: Array<Int>): Double
```

### Vocabulary (id to strint)
```kotlin
vocab: Array<String>
```

### Vocabulary (string to id)
```kotlin
getId(word: String) : Int
```


