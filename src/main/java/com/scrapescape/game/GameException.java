package com.scrapescape.game;

import org.springframework.http.HttpStatus;
final class GameException extends RuntimeException {
    final HttpStatus status;
    final String code;
    GameException(HttpStatus status, String code, String message) { super(message); this.status=status; this.code=code; }
    static GameException conflict(String code,String message) { return new GameException(HttpStatus.CONFLICT,code,message); }
    static GameException missing(String message) { return new GameException(HttpStatus.NOT_FOUND,"NOT_FOUND",message); }
}
