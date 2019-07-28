import java.io.File
import kotlin.Exception

class LMFormatException(message: String) : Exception(message) { }

class IDTupleArray(_n: Int, _size: Int) {
    val n = _n
    val size = _size
    val body = Array<Int>(n*size, {_ -> 0})

    fun setTuple(i: Int, tuple:Array<Int>) {
        val pos = i*n
        for (k in 0..(n-1)) {
            body[pos+k] = tuple[k]
        }
    }

    fun compare(i: Int, tuple:Array<Int>): Int {
        val pos = i*n
        for (k in 0..(n-1)) {
            if (body[pos+k] < tuple[k])
                return -1
            else if (body[pos+k] > tuple[k])
                return 1
        }
        return 0
    }

    fun findTuple(tuple: Array<Int>) : Int {
        var b = 0
        var e = size-1
        var h: Int
        while (b <= e) {
            h = (b+e)/2
            val c = compare(h,tuple)
            if (c < 0) b = h+1
            else if (c > 0) e = h-1
            else
                return h
        }
        return -1
    }
}

interface LMGenericProbs {
    fun setTuple(i: Int, tuple: Array<Int>)
    fun getid(tuple: Array<Int>): Int
    fun getlprob(i: Int) : Float
    fun setlprob(i: Int, value:Float)
    fun getalpha(i: Int): Float
    fun setalpha(i: Int, value:Float)
    fun getlprob(w: Array<Int>): Float {
        return getlprob(getid(w))
    }
    fun getalpha(w: Array<Int>): Float {
        return getalpha(getid(w))
    }
    fun setlprob(w: Array<Int>, value: Float) {
        setlprob(getid(w),value)
    }
    fun setalpha(w: Array<Int>, value: Float) {
        setalpha(getid(w),value)
    }
}

open class LMProbs(_n: Int, _size: Int) : LMGenericProbs {
    val n = _n
    val size = _size
    val lprobs = Array<Float>(size, {_->0.0f})
    val ids = IDTupleArray(n,size)
    override fun getid(tuple: Array<Int>): Int {
        return ids.findTuple(tuple)
    }
    override fun getlprob(i: Int): Float {
        return lprobs[i]
    }
    override fun setlprob(i: Int, value: Float) {
        lprobs[i] = value
    }
    override fun getalpha(i: Int): Float = 0f
    override fun setalpha(i: Int, value: Float) {}
    override fun setTuple(i: Int, tuple: Array<Int>) {
        ids.setTuple(i,tuple)
    }
}

class LMIntermediateProbs(_n: Int, _size: Int) : LMProbs(_n,_size), LMGenericProbs {
    val alpha = Array<Float>(size, {_->0.0f})
    override fun getalpha(i: Int): Float = alpha[i]
    override fun setalpha(i: Int, value: Float) {
        alpha[i] = value
    }
}

// N-gram language model
class ARPALM {
    enum class ARPAStatus {
        PREAMBLE, // Before \data\ section
        DATA,  // in \data\ section
        GRAM  // \1-gram, \2-gram, ...
    }
    val maxGramLength = 10
    var maxGram = 0
    var vocab: Array<String>? = null
    var rvocab = HashMap<String, Int>()
    val gramsize = Array<Int>(maxGramLength, {_->0})
    val lmprobs = Array<LMGenericProbs?>(maxGramLength, {_->null})
    constructor(arpafile: String) {
        val afile = File(arpafile)
        val instream = afile.bufferedReader()
        var status = ARPAStatus.PREAMBLE
        val gramRegex = Regex("""(\d+)=(\d+)""")
        val gramSeparator = Regex("""^\\\d-grams:""")
        val emptyLine = Regex("""^\s*$""")
        val spaces = Regex("""\s+""")
        var currentGram = 0
        var currentId = 0
        mainloop@ while (true) {
            val line = instream.readLine()
            if (line == null)
                break
            when (status) {
                ARPAStatus.PREAMBLE -> {
                    if (line.startsWith("\\data\\")) {
                        status = ARPAStatus.DATA
                    }
                }
                ARPAStatus.DATA -> {
                    if (line.startsWith("ngram ")) {
                        val result = gramRegex.find(line)
                        if (result == null) {
                            throw LMFormatException("Invalid ngram= line")
                        }
                        val n = result.groupValues[1].toInt()
                        val size = result.groupValues[2].toInt()
                        if (n > maxGramLength) {
                            throw LMFormatException("N-gram too long (longer than "+ maxGramLength.toString()+")")
                        }
                        maxGram = n
                        gramsize[n-1] = size
                    } else if (line.startsWith("\\1-grams:")) {
                        status = ARPAStatus.GRAM
                        currentGram = 1
                        currentId = 0
                        for (i:Int in 0 until maxGram) {
                            if (i == maxGram-1) {
                                lmprobs[i] = LMProbs(i+1,gramsize[i])
                            } else {
                                lmprobs[i] = LMIntermediateProbs(i+1,gramsize[i])
                            }
                        }
                        vocab = Array<String>(gramsize[0],{_->""})
                        rvocab = HashMap<String,Int>()
                    }
                }
                ARPAStatus.GRAM -> {
                    if (gramSeparator.matches(line)) {
                        currentGram++
                        currentId = 0
                    } else if (emptyLine.matches(line)) {
                        continue@mainloop
                    } else if (line.startsWith("\\end\\")) {
                        break@mainloop
                    } else {
                        val lcontents = line.trim().split(spaces)
                        if ((currentGram == maxGram && lcontents.size != currentGram+1) ||
                             (currentGram < maxGram && lcontents.size != currentGram+2)) {
                            throw LMFormatException("Invalid line: reading ${currentGram}-gram, line has ${lcontents.size} fields\n+Line: "+line)
                        }
                        if (currentGram == 1) {
                            // if unigram, then register the vocab
                            vocab!![currentId] = lcontents[1]
                            rvocab.put(lcontents[1],currentId)
                        }
                        val lprob = lcontents[0].toFloat()
                        val tuple = Array<Int>(currentGram,{i -> getId(lcontents[i+1])})
                        lmprobs[currentGram-1]?.setTuple(currentId,tuple)
                        lmprobs[currentGram-1]?.setlprob(currentId,lprob)
                        if (currentGram < maxGram) {
                            val alpha = lcontents[currentGram+1].toFloat()
                            lmprobs[currentGram-1]?.setalpha(currentId,alpha)
                        }
                        currentId++
                    }
                }
            }
        }
        instream.close()
    }
    fun getId(w:String):Int {
        val id = rvocab[w]
        if (id == null) {
            return rvocab["<UNK>"]!!
        }
        return id
    }
    fun getBOlprob0(tuple: Array<Int>) : Double {
        val len = tuple.size
        var id: Int
        if (len == 1) {
            id = tuple[0]
            if (id < 0 || id >= vocab!!.size)
                id = rvocab.get("<UNK>")!!
            return lmprobs[0]!!.getlprob(id).toDouble()
        }
        id = lmprobs[len-1]!!.getid(tuple)
        if (id >= 0) {
            val lp = lmprobs[len-1]!!.getlprob(id)
            //val w = vocab!![tuple[1]]
            //println("${len}-gram ${id} ${w} has logprob ${lp}")
            return lp.toDouble()
        }
        val p = getBOlprob0(tuple.sliceArray(1..(tuple.size-1)))
        id = lmprobs[len-2]!!.getid(tuple.sliceArray(0..(tuple.size-2)))
        if (id < 0) {
            //println("${len}-gram reduced: lprob=${p}")
            return p
        } else {
            val alpha= lmprobs[len-2]!!.getalpha(id)
            //println("${len}-gram backoff; alpha=${alpha} p=${p} lprob=${p+alpha}")
            return p+alpha
        }
    }
    fun getBOlprob(w:Array<String>): Double {
        var len = w.size
        if (len > maxGram) {
            len = maxGram
        }
        val tuple = Array<Int>(len,{i -> getId(w[w.size-len+i])})
        return getBOlprob0(tuple)
    }
    fun getBOlprob(w:Array<Int>): Double {
        var tuple = w
        var len = w.size
        if (len > maxGram) {
            len = maxGram
            tuple = Array<Int>(len,{i -> w[w.size-len+i]})
        }
        return getBOlprob0(tuple)
    }

}