package me.anno.ui.base.components

class Padding(l: Int, t: Int, r: Int, b: Int): LTRB(l,t,r,b){
    constructor(all: Int): this(all,all,all,all)

    companion object {
        val Zero = LTRB(0,0,0,0)
    }

}