package com.ghostchu.peerbanhelper.module;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.function.Consumer;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PeerMatchContext {
    private PeerMatchRecord record;
    private Consumer<PeerMatchRecord> activeFunc;
    private Consumer<PeerMatchRecord> banFunc;
    private Consumer<PeerMatchRecord> disconnectFunc;
    private Consumer<PeerMatchRecord> timeoutFunc;
}
