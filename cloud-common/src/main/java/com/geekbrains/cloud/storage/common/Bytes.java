package com.geekbrains.cloud.storage.common;

public enum Bytes {
    BYTE_OF_CONFIRM
            {public byte toByte(){return 10;}},
    BYTE_OF_REFRESH
            {public byte toByte(){return 11;}},
    BYTE_OF_NEW_USER
            {public byte toByte(){return 12;}},
    BYTE_OF_NEW_USER_RIGHT
            {public byte toByte(){return 13;}},
    BYTE_OF_AUTH
            {public byte toByte(){return 14;}},
    BYTE_OF_AUTH_RIGHT
            {public byte toByte(){return 15;}},
    BYTE_OF_COPY_FILE
            {public byte toByte(){return 16;}},
    BYTE_OF_MOVE_FILE
            {public byte toByte(){return 17;}},
    BYTE_OF_DELETE_FILE
            {public byte toByte(){return 18;}},
    BYTE_OF_RENAME_FILE
            {public byte toByte(){return 19;}},
    BYTE_OF_SEND_FILE_FROM_SERVER
            {public byte toByte(){return 20;}},
    BYTE_OF_SEND_FILE_FROM_CLIENT
            {public byte toByte(){return 21;}},
    BYTE_OF_ENTER_CATALOG
            {public byte toByte(){return 22;}},
    BYTE_OF_SEND_CATALOG_FROM_CLIENT
            {public byte toByte(){return 23;}},
    BYTE_OF_SEND_CATALOG_FROM_SERVER
            {public byte toByte(){return 24;}},
    BYTE_OF_CATALOG_LEVEL_UP
            {public byte toByte(){return 25;}},
    BYTE_OF_END_OF_SEND_FROM_SERVER
            {public byte toByte(){return 26;}},
    BYTE_OF_CONFIRM_GET_FILE
            {public byte toByte(){return 27;}};

    public abstract byte toByte();
}
