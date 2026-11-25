package com.zirakusa.camara.data

/**
 * «Video dual» rules (pure, with tests): whether the phone can use the back and the front camera at once, and where each
 * goes in the joined picture. Lens facings are CameraX's numbers (front 0, back 1).
 */
object Dual {
    const val FRONT = 0
    const val BACK = 1
    /** Each camera at half size (the same both ways, so nobody looks stretched)… */
    const val SCALE = 0.5f
    /** …one on each half of the picture: the back camera on one, the front one on the other. */
    const val OFFSET = 0.5f

    /** The phone lists the cameras it can open together: «Video dual» needs a set with a back and a front one. */
    fun possible(sets: List<List<Int?>>): Boolean = sets.any { BACK in it && FRONT in it }
}
