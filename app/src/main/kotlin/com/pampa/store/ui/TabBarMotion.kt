package com.pampa.store.ui

/**
 * Quanto la barra delle schede (o il rail) è uscita dallo schermo: 0 è al suo posto, 1 è fuori.
 *
 * La barra è una sola e sta fuori dall'host delle pagine, quindi non viaggia con nessuna pagina:
 * esce insieme al dettaglio che si apre e rientra insieme a quello che si chiude, **col dito**. Per
 * questo segue l'avanzamento dell'host e non un interruttore: un back a scorrimento a metà strada
 * lascia la barra a metà strada, e un back annullato la riporta dov'era.
 *
 * A riposo ([moving] falso) vale solo [restHidden], cioè se la pagina davanti non ha la barra. In
 * movimento si va da come stava la pagina che lascia il davanti ([fromHidden]) a come sta quella che
 * lo prende ([toHidden]), di quanto l'host dice che il movimento è fatto ([progress], da 0 a 1).
 * Fra due schede non cambia niente: tutte e due hanno la barra.
 */
internal fun tabBarHidden(
    moving: Boolean,
    fromHidden: Boolean,
    toHidden: Boolean,
    progress: Float,
    restHidden: Boolean,
): Float {
    if (!moving) return if (restHidden) 1f else 0f
    val from = if (fromHidden) 1f else 0f
    val to = if (toHidden) 1f else 0f
    return from + (to - from) * progress.coerceIn(0f, 1f)
}
