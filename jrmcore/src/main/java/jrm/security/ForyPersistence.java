/*
 * Copyright (C) 2024 optyfr This program is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the License, or (at your option) any
 * later version. This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details. You should
 * have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package jrm.security;

import java.io.File;

import org.apache.fory.Fory;
import org.apache.fory.ThreadSafeFory;

import jrm.batch.DirUpdaterResults;
import jrm.batch.TrntChkReport;
import jrm.profile.Profile;
import jrm.profile.data.Anyware;
import jrm.profile.data.AnywareBase;
import jrm.profile.data.AnywareList;
import jrm.profile.data.AnywareListList;
import jrm.profile.data.AnywareStatus;
import jrm.profile.data.Archive;
import jrm.profile.data.Container;
import jrm.profile.data.Device;
import jrm.profile.data.Directory;
import jrm.profile.data.Disk;
import jrm.profile.data.Driver;
import jrm.profile.data.Entity;
import jrm.profile.data.EntityBase;
import jrm.profile.data.EntityStatus;
import jrm.profile.data.Entry;
import jrm.profile.data.FakeDirectory;
import jrm.profile.data.Input;
import jrm.profile.data.Machine;
import jrm.profile.data.MachineList;
import jrm.profile.data.MachineListList;
import jrm.profile.data.Rom;
import jrm.profile.data.Sample;
import jrm.profile.data.Samples;
import jrm.profile.data.SamplesList;
import jrm.profile.data.Slot;
import jrm.profile.data.SlotOption;
import jrm.profile.data.Software;
import jrm.profile.data.SoftwareList;
import jrm.profile.data.SoftwareListList;
import jrm.profile.data.Systm;
import jrm.profile.manager.ProfileNFO;
import jrm.profile.manager.ProfileNFOMame;
import jrm.profile.manager.ProfileNFOStats;
import jrm.profile.report.ContainerTZip;
import jrm.profile.report.ContainerUnknown;
import jrm.profile.report.ContainerUnneeded;
import jrm.profile.report.EntryAdd;
import jrm.profile.report.EntryMissing;
import jrm.profile.report.EntryMissingDuplicate;
import jrm.profile.report.EntryOK;
import jrm.profile.report.EntryUnneeded;
import jrm.profile.report.EntryWrongHash;
import jrm.profile.report.EntryWrongName;
import jrm.profile.report.Report;
import jrm.profile.report.RomSuspiciousCRC;
import jrm.profile.report.Subject;
import jrm.profile.report.SubjectSet;
import jtrrntzip.TrrntZipStatus;

/**
 * Owns the three reused {@link ThreadSafeFory} instances used by {@link SignedObjectStore}.
 * Registration lists are the allowlist; IDs are stable per codec instance.
 */
public final class ForyPersistence {

    static final int CACHE_DEPTH = 100;
    static final int REPORT_DEPTH = 100;
    static final int TRNTCHK_DEPTH = 1_000_000;

    private static final ThreadSafeFory CACHE = create(CACHE_DEPTH);
    private static final ThreadSafeFory REPORT = create(REPORT_DEPTH);
    private static final ThreadSafeFory TRNTCHK = create(TRNTCHK_DEPTH);

    static {
        registerShared(CACHE);
        registerProfileData(CACHE);
        registerNfo(CACHE);
        registerShared(REPORT);
        registerProfileData(REPORT);
        registerReport(REPORT);
        registerTrntChk(TRNTCHK);
    }

    private ForyPersistence() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Returns the reused Fory instance for {@code codec}.
     *
     * @param codec the persistence codec
     * @return the thread-safe Fory instance
     */
    public static ThreadSafeFory get(final SignedObjectStore.Codec codec) {
        return switch (codec) {
            case CACHE -> CACHE;
            case REPORT -> REPORT;
            case TRNTCHK -> TRNTCHK;
        };
    }

    private static ThreadSafeFory create(final int depth) {
        return Fory.builder()
                .withXlang(false)
                .requireClassRegistration(true)
                .withCompatible(false)
                .withRefTracking(true)
                .withMaxDepth(depth)
                .withDeserializeUnknownClass(false)
                .withJdkClassSerializableCheck(true)
                .buildThreadSafeFory();
    }

    static void registerShared(final ThreadSafeFory fory) {
        fory.register(File.class, 100);
        fory.register(TrrntZipStatus.class, 103);
        fory.register(Container.class, 104);
        fory.register(Archive.class, 105);
        fory.register(Directory.class, 106);
        fory.register(FakeDirectory.class, 107);
        fory.register(Entry.class, 108);
        fory.register(Container.Type.class, 180);
        fory.register(Entry.Type.class, 181);
    }

    static void registerProfileData(final ThreadSafeFory fory) {
        fory.register(Profile.class, 200);
        registerNamed(fory, "jrm.profile.data.NameBase", 201);
        fory.register(AnywareBase.class, 202);
        fory.register(Anyware.class, 203);
        fory.register(AnywareList.class, 204);
        fory.register(AnywareListList.class, 205);
        fory.register(Machine.class, 206);
        fory.register(Machine.SWList.class, 207);
        fory.register(MachineList.class, 208);
        fory.register(MachineListList.class, 209);
        fory.register(Software.class, 210);
        fory.register(Software.Part.class, 211);
        fory.register(Software.Part.DataArea.class, 212);
        fory.register(Software.Part.DiskArea.class, 213);
        fory.register(SoftwareList.class, 214);
        fory.register(SoftwareListList.class, 215);
        fory.register(EntityBase.class, 216);
        fory.register(Entity.class, 217);
        fory.register(Rom.class, 218);
        fory.register(Disk.class, 219);
        fory.register(Sample.class, 220);
        fory.register(Samples.class, 221);
        fory.register(SamplesList.class, 222);
        fory.register(Device.class, 223);
        fory.register(Device.Instance.class, 224);
        fory.register(Device.Extension.class, 225);
        fory.register(Driver.class, 226);
        fory.register(Input.class, 227);
        fory.register(Slot.class, 228);
        fory.register(SlotOption.class, 229);
        fory.register(AnywareStatus.class, 230);
        fory.register(EntityStatus.class, 231);
        fory.register(Entity.Status.class, 232);
        fory.register(Rom.LoadFlag.class, 233);
        fory.register(Driver.StatusType.class, 234);
        fory.register(Driver.SaveStateType.class, 235);
        fory.register(Machine.SWStatus.class, 236);
        fory.register(Machine.DisplayOrientation.class, 237);
        fory.register(Machine.CabinetType.class, 238);
        fory.register(Software.Supported.class, 239);
        fory.register(Software.Part.DataArea.Endianness.class, 240);
        fory.register(Systm.Type.class, 241);
    }

    static void registerNfo(final ThreadSafeFory fory) {
        fory.register(ProfileNFO.class, 400);
        fory.register(ProfileNFOStats.class, 401);
        fory.register(ProfileNFOMame.class, 402);
    }

    static void registerReport(final ThreadSafeFory fory) {
        fory.register(Report.class, 500);
        fory.register(Report.Stats.class, 501);
        fory.register(Subject.class, 502);
        fory.register(SubjectSet.class, 503);
        fory.register(SubjectSet.Status.class, 504);
        registerNamed(fory, "jrm.profile.report.ContainerSubject", 505);
        fory.register(ContainerUnknown.class, 506);
        fory.register(ContainerUnneeded.class, 507);
        fory.register(ContainerTZip.class, 508);
        fory.register(RomSuspiciousCRC.class, 509);
        fory.register(jrm.profile.report.Note.class, 510);
        registerNamed(fory, "jrm.profile.report.EntryNote", 511);
        registerNamed(fory, "jrm.profile.report.EntryExtNote", 512);
        fory.register(EntryAdd.class, 513);
        fory.register(EntryOK.class, 514);
        fory.register(EntryMissing.class, 515);
        fory.register(EntryMissingDuplicate.class, 516);
        fory.register(EntryUnneeded.class, 517);
        fory.register(EntryWrongName.class, 518);
        fory.register(EntryWrongHash.class, 519);
        fory.register(DirUpdaterResults.class, 520);
        fory.register(DirUpdaterResults.DirUpdaterResult.class, 521);
    }

    static void registerTrntChk(final ThreadSafeFory fory) {
        fory.register(TrntChkReport.class, 600);
        fory.register(TrntChkReport.Child.class, 601);
        fory.register(TrntChkReport.ChildData.class, 602);
        fory.register(TrntChkReport.Status.class, 603);
    }

    private static void registerNamed(final ThreadSafeFory fory, final String className, final int id) {
        try {
            fory.register(Class.forName(className), id);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }
}
