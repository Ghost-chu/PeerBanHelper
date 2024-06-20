package com.ghostchu.peerbanhelper.module;

import com.ghostchu.peerbanhelper.downloader.Downloader;
import com.ghostchu.peerbanhelper.peer.Peer;
import com.ghostchu.peerbanhelper.torrent.Torrent;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Peer匹配记录
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PeerMatchRecord {
    Downloader downloader;
    Torrent torrent;
    Peer peer;
    MatchResultDetail result;
}
