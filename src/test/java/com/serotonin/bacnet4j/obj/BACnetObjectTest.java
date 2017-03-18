package com.serotonin.bacnet4j.obj;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.serotonin.bacnet4j.LocalDevice;
import com.serotonin.bacnet4j.RemoteDevice;
import com.serotonin.bacnet4j.TestUtils;
import com.serotonin.bacnet4j.exception.BACnetException;
import com.serotonin.bacnet4j.npdu.test.TestNetwork;
import com.serotonin.bacnet4j.transport.DefaultTransport;
import com.serotonin.bacnet4j.type.constructed.BACnetArray;
import com.serotonin.bacnet4j.type.constructed.DateTime;
import com.serotonin.bacnet4j.type.constructed.NameValue;
import com.serotonin.bacnet4j.type.constructed.Recipient;
import com.serotonin.bacnet4j.type.constructed.SequenceOf;
import com.serotonin.bacnet4j.type.enumerated.ErrorClass;
import com.serotonin.bacnet4j.type.enumerated.ErrorCode;
import com.serotonin.bacnet4j.type.enumerated.ObjectType;
import com.serotonin.bacnet4j.type.enumerated.PropertyIdentifier;
import com.serotonin.bacnet4j.type.primitive.BitString;
import com.serotonin.bacnet4j.type.primitive.CharacterString;
import com.serotonin.bacnet4j.type.primitive.Null;
import com.serotonin.bacnet4j.type.primitive.ObjectIdentifier;
import com.serotonin.bacnet4j.type.primitive.Real;
import com.serotonin.bacnet4j.type.primitive.SignedInteger;
import com.serotonin.bacnet4j.util.RequestUtils;

public class BACnetObjectTest {
    LocalDevice d1;
    LocalDevice d2;
    RemoteDevice rd2;

    @Before
    public void before() throws Exception {
        d1 = new LocalDevice(1, new DefaultTransport(new TestNetwork(1, 0)));
        d1.initialize();

        d2 = new LocalDevice(2, new DefaultTransport(new TestNetwork(2, 0)));
        d2.writePropertyInternal(PropertyIdentifier.tags,
                new BACnetArray<>( //
                        new NameValue("tag1", DateTime.UNSPECIFIED), //
                        new NameValue("tag2", new Real(234)), //
                        new NameValue("tag2", Null.instance)));
        d2.writePropertyInternal(PropertyIdentifier.forId(6789),
                new BACnetArray<>(new Real(0), new Real(1), new Real(2)));
        d2.initialize();

        // Announce d1 to d2.
        d1.sendGlobalBroadcast(d1.getIAm());
        d2.sendGlobalBroadcast(d2.getIAm());

        // Wait a bit
        Thread.sleep(100);

        // Get d1 as a remote object.
        rd2 = d1.getRemoteDevice(2).get();

    }

    @After
    public void after() throws InterruptedException {
        Thread.sleep(50);

        // Shut down
        d1.terminate();
        d2.terminate();
    }

    @Test
    public void definedScalarWithIncorrectType() throws BACnetException {
        // TODO this should not work.
        //        TestUtils.assertErrorAPDUException(() -> {
        RequestUtils.writeProperty(d1, rd2, d2.getId(), PropertyIdentifier.description, new Real(0));
        //        }, ErrorClass.property, ErrorCode.invalidDataType);
        System.out.println(d2.getProperty(PropertyIdentifier.description));
    }

    @Test
    public void definedScalarWithCorrectType() throws BACnetException {
        RequestUtils.writeProperty(d1, rd2, d2.getId(), PropertyIdentifier.description, new CharacterString("a"));
        Assert.assertEquals(new CharacterString("a"), d2.getProperty(PropertyIdentifier.description));
    }

    @Test
    public void undefinedScalar() throws BACnetException {
        RequestUtils.writeProperty(d1, rd2, d2.getId(), PropertyIdentifier.forId(5557), new Real(55));
        Assert.assertEquals(new Real(55), d2.getProperty(PropertyIdentifier.forId(5557)));

        RequestUtils.writeProperty(d1, rd2, d2.getId(), PropertyIdentifier.forId(5557), new CharacterString("b"));
        Assert.assertEquals(new CharacterString("b"), d2.getProperty(PropertyIdentifier.forId(5557)));
    }

    @Test
    public void definedArrayWriteElementIncorrectType() {
        // Write a primitive type instead of a NameValue
        TestUtils.assertErrorAPDUException(() -> {
            RequestUtils.writeProperty(d1, rd2, d2.getId(), PropertyIdentifier.tags, 3,
                    new BitString(new boolean[] { false }));
        }, ErrorClass.property, ErrorCode.missingRequiredParameter);
    }

    @Test
    public void definedArrayWriteElementCorrectType() throws BACnetException {
        RequestUtils.writeProperty(d1, rd2, d2.getId(), PropertyIdentifier.tags, 3,
                new NameValue("t3", new Real(3.14F)));
        Assert.assertEquals(new BACnetArray<>( //
                new NameValue("tag1", DateTime.UNSPECIFIED), //
                new NameValue("tag2", new Real(234)), //
                new NameValue("t3", new Real(3.14F))), d2.getProperty(PropertyIdentifier.tags));
    }

    @Test
    public void definedArrayWriteIncorrectInnerType() {
        TestUtils.assertErrorAPDUException(() -> {
            RequestUtils.writeProperty(d1, rd2, d2.getId(), PropertyIdentifier.tags,
                    new SequenceOf<>(new Real(-1), new Real(0), new Real(1)));
        }, ErrorClass.property, ErrorCode.missingRequiredParameter);
    }

    @Test
    public void definedArrayWriteIncorrectType() {
        TestUtils.assertErrorAPDUException(() -> {
            RequestUtils.writeProperty(d1, rd2, d2.getId(), PropertyIdentifier.tags, new Real(1));
        }, ErrorClass.property, ErrorCode.missingRequiredParameter);
    }

    @Test
    public void definedArrayWriteCorrectType() throws BACnetException {
        RequestUtils.writeProperty(d1, rd2, d2.getId(), PropertyIdentifier.tags,
                new SequenceOf<>( //
                        new NameValue("t1", new CharacterString("v1")), //
                        new NameValue("t2", DateTime.UNSPECIFIED)));
        Assert.assertEquals(new BACnetArray<>( //
                new NameValue("t1", new CharacterString("v1")), //
                new NameValue("t2", DateTime.UNSPECIFIED)), d2.getProperty(PropertyIdentifier.tags));
    }

    @Test
    public void undefinedArrayWrite() {
        // Fails because the property isn't parsable.
        TestUtils.assertErrorAPDUException(() -> {
            RequestUtils.writeProperty(d1, rd2, d2.getId(), PropertyIdentifier.forId(5678),
                    new SequenceOf<>(new Real(-0.1f), new Real(0), new Real(0.1f)));
        }, ErrorClass.property, ErrorCode.missingRequiredParameter);
    }

    @Test
    public void undefinedArrayWriteElementLowIndex() {
        // Returns invalid data type because the index of 0 indicates a write to the array length.
        TestUtils.assertErrorAPDUException(() -> {
            RequestUtils.writeProperty(d1, rd2, d2.getId(), PropertyIdentifier.forId(6789), 0, new Real(10));
        }, ErrorClass.property, ErrorCode.invalidDataType);
    }

    @Test
    public void undefinedArrayWriteElementHighIndex() {
        TestUtils.assertErrorAPDUException(() -> {
            RequestUtils.writeProperty(d1, rd2, d2.getId(), PropertyIdentifier.forId(6789), 4, new Real(10));
        }, ErrorClass.property, ErrorCode.invalidArrayIndex);
    }

    @Test
    public void undefinedArrayWriteElement() throws BACnetException {
        RequestUtils.writeProperty(d1, rd2, d2.getId(), PropertyIdentifier.forId(6789), 2, new Real(10));
        Assert.assertEquals(new BACnetArray<>(new Real(0), new Real(10), new Real(2)),
                d2.getProperty(PropertyIdentifier.forId(6789)));
    }

    @Test
    public void undefinedArrayWriteIncorrectElement() {
        TestUtils.assertErrorAPDUException(() -> {
            RequestUtils.writeProperty(d1, rd2, d2.getId(), PropertyIdentifier.forId(6789), 0, new SignedInteger(10));
        }, ErrorClass.property, ErrorCode.invalidDataType);
    }

    @Test
    public void definedListWriteCorrectType() throws BACnetException {
        RequestUtils.writeProperty(d1, rd2, d2.getId(), PropertyIdentifier.restartNotificationRecipients,
                new SequenceOf<>( //
                        new Recipient(new ObjectIdentifier(ObjectType.device, 10)), //
                        new Recipient(new ObjectIdentifier(ObjectType.device, 11)), //
                        new Recipient(new ObjectIdentifier(ObjectType.device, 12)), //
                        new Recipient(new ObjectIdentifier(ObjectType.device, 13))));
        Assert.assertEquals(
                new SequenceOf<>( //
                        new Recipient(new ObjectIdentifier(ObjectType.device, 10)), //
                        new Recipient(new ObjectIdentifier(ObjectType.device, 11)), //
                        new Recipient(new ObjectIdentifier(ObjectType.device, 12)), //
                        new Recipient(new ObjectIdentifier(ObjectType.device, 13))),
                d2.getProperty(PropertyIdentifier.restartNotificationRecipients));
    }
}