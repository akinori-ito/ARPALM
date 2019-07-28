import kotlin.random.Random

fun main(args: Array<String>) {
    val lmfile = """C:\Users\user\Dropbox\Documents\ngram\lyrics_tiny.arpa"""
    val lm = ARPALM(lmfile)
    val vocabsize = lm.vocab?.size
    println("Vocabulary size: ${vocabsize}")
    if (vocabsize == null)
        return
    val mywords = arrayOf("<UNK>","<UNK>","<UNK>")
    println("lprob(UNK trigram)="+lm.getBOlprob(mywords).toString())
    val word1 = Array<Int>(1,{_->0})
    var total: Double = 0.0
    print("Unigram test: ")
    for (i in 0 until vocabsize) {
        word1[0] = i
        val p = lm.getBOlprob(word1)
        total += Math.pow(10.0,p)
    }
    println(total)
    println("Bigram test: ")
    val word2 = Array<Int>(2,{_->0})
    for (iter in 1..30) {
        word2[0] = Random.nextInt(vocabsize)
        total = 0.0
        for (i in 0 until vocabsize) {
            word2[1] = i
            val p = lm.getBOlprob(word2)
            total += Math.pow(10.0, p)
        }
        println("P(*|" + lm.vocab!![word2[0]] + ")=" + total.toString())
    }
}