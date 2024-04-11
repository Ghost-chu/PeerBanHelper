package com.ghostchu.peerbanhelper.module.impl;

import com.ghostchu.peerbanhelper.config.ModuleBaseConfigSection;
import com.ghostchu.peerbanhelper.config.section.ModuleIPBlacklistConfigSection;
import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.module.BanResult;
import com.ghostchu.peerbanhelper.module.PeerAction;
import com.ghostchu.peerbanhelper.peer.Peer;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.torrent.Torrent;
import com.ghostchu.peerbanhelper.wrapper.PeerAddress;
import inet.ipaddr.AddressStringException;
import inet.ipaddr.IPAddress;
import inet.ipaddr.IPAddressString;

import java.util.List;
import java.util.concurrent.ExecutorService;

public class IPBlackList extends AbstractFeatureModule<ModuleIPBlacklistConfigSection> {

    public IPBlackList(ModuleIPBlacklistConfigSection section) {
        super(section);
    }

    @Override
    public String getName() {
        return "IP Blacklist";
    }

    @Override
    public BanResult shouldBanPeer(Torrent torrent, Peer peer, ExecutorService ruleExecuteExecutor) {
        List<String> ips = getConfig().getIps();
        List<Integer> ports = getConfig().getPorts();

        PeerAddress peerAddress = peer.getAddress();

        if (ports.contains(peerAddress.getPort())) {
            return new BanResult(this,PeerAction.BAN, "Restricted ports");
        }
        for (String ip : ips) {
            if (peerAddress.getIp().equals(ip.trim())) {
                return new BanResult(this,PeerAction.BAN, String.format(Lang.MODULE_IBL_MATCH_IP, ip));
            }
            try {
                IPAddress address = new IPAddressString(ip).toAddress();
                if (address.contains(new IPAddressString(peerAddress.getIp()).toAddress())) {
                    return new BanResult(this,PeerAction.BAN, String.format(Lang.MODULE_IBL_MATCH_IP, ip));
                }
            } catch (AddressStringException ignored) {
            }
        }
        return new BanResult(this,PeerAction.NO_ACTION, "No matches");
    }
}
