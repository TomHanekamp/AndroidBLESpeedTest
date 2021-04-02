package nl.tomhanekamp.blespeedtest.server.model

class Response {
    var greetings: String? = null

    constructor(greetings: String?) {
        this.greetings = greetings
    }

    constructor() {}
}