// macOS virtual display for filling in when the Mac has no usable display (lid closed, no monitor).
// CoreGraphics has private CGVirtualDisplay* classes (what Sidecar and AirPlay displays are built on; DeskPad,
// BetterDisplay and Chromium's tests use them too). They have no public header, so everything is looked up at run
// time and checked before use: if a macOS release changes them we report an error instead of crashing.
#import <CoreGraphics/CoreGraphics.h>
#import <Foundation/Foundation.h>
#include <stdio.h>
#include <unistd.h>

@interface CGVirtualDisplayDescriptor : NSObject
@property(retain, nonatomic) dispatch_queue_t queue;
@property(retain, nonatomic) NSString *name;
@property(nonatomic) unsigned int maxPixelsWide;
@property(nonatomic) unsigned int maxPixelsHigh;
@property(nonatomic) CGSize sizeInMillimeters;
@property(nonatomic) unsigned int productID;
@property(nonatomic) unsigned int vendorID;
@property(nonatomic) unsigned int serialNum;
@end

@interface CGVirtualDisplayMode : NSObject
- (instancetype)initWithWidth:(unsigned int)width height:(unsigned int)height refreshRate:(double)refreshRate;
@end

@interface CGVirtualDisplaySettings : NSObject
@property(retain, nonatomic) NSArray *modes;
@property(nonatomic) unsigned int hiDPI;
@end

@interface CGVirtualDisplay : NSObject
- (instancetype)initWithDescriptor:(CGVirtualDisplayDescriptor *)descriptor;
- (BOOL)applySettings:(CGVirtualDisplaySettings *)settings;
@property(readonly, nonatomic) unsigned int displayID;
@end

static Class descriptorClass(void) { return NSClassFromString(@"CGVirtualDisplayDescriptor"); }
static Class modeClass(void) { return NSClassFromString(@"CGVirtualDisplayMode"); }
static Class settingsClass(void) { return NSClassFromString(@"CGVirtualDisplaySettings"); }
static Class displayClass(void) { return NSClassFromString(@"CGVirtualDisplay"); }

static void *failure(char *error, size_t size, NSString *what) {
    snprintf(error, size, "%s", what.UTF8String);
    return NULL;
}

int ah_vd_supported(void) {
    Class d = descriptorClass(), m = modeClass(), s = settingsClass(), v = displayClass();
    return d && m && s && v &&
           [d instancesRespondToSelector:@selector(setQueue:)] &&
           [d instancesRespondToSelector:@selector(setMaxPixelsWide:)] &&
           [d instancesRespondToSelector:@selector(setMaxPixelsHigh:)] &&
           [d instancesRespondToSelector:@selector(setSizeInMillimeters:)] &&
           [m instancesRespondToSelector:@selector(initWithWidth:height:refreshRate:)] &&
           [s instancesRespondToSelector:@selector(setModes:)] &&
           [s instancesRespondToSelector:@selector(setHiDPI:)] &&
           [v instancesRespondToSelector:@selector(initWithDescriptor:)] &&
           [v instancesRespondToSelector:@selector(applySettings:)] &&
           [v instancesRespondToSelector:@selector(displayID)];
}

// One mode, `width` x `height`: points when `hidpi` (the frame buffer is twice as large), pixels otherwise.
// Returns the display (retained: it exists until ah_vd_destroy) and its CGDirectDisplayID in `display`.
void *ah_vd_create(uint32_t width, uint32_t height, int hidpi, double hz, double mm_w, double mm_h,
                   uint32_t vendor, uint32_t product, uint32_t serial, const char *name,
                   uint32_t *display, char *error, size_t size) {
    @autoreleasepool {
        *display = kCGNullDirectDisplay;
        if (!ah_vd_supported()) return failure(error, size, @"this macOS has no usable CGVirtualDisplay");
        @try {
            static dispatch_queue_t queue;
            static dispatch_once_t once;
            dispatch_once(&once, ^{
                queue = dispatch_queue_create("app.airhand.virtual-display", DISPATCH_QUEUE_SERIAL);
            });
            uint32_t scale = hidpi ? 2 : 1;
            CGVirtualDisplayDescriptor *descriptor = [[descriptorClass() alloc] init];
            descriptor.queue = queue;
            if ([descriptor respondsToSelector:@selector(setName:)]) descriptor.name = @(name);
            descriptor.maxPixelsWide = width * scale;
            descriptor.maxPixelsHigh = height * scale;
            // The physical size decides how large things look (the default "looks like" size)
            descriptor.sizeInMillimeters = CGSizeMake(mm_w, mm_h);
            // Fixed identity: macOS remembers the arrangement and the windows' places per display
            if ([descriptor respondsToSelector:@selector(setVendorID:)]) descriptor.vendorID = vendor;
            if ([descriptor respondsToSelector:@selector(setProductID:)]) descriptor.productID = product;
            if ([descriptor respondsToSelector:@selector(setSerialNum:)]) descriptor.serialNum = serial;

            CGVirtualDisplay *virtualDisplay = [[displayClass() alloc] initWithDescriptor:descriptor];
            if (!virtualDisplay) return failure(error, size, @"CGVirtualDisplay refused the descriptor");
            CGVirtualDisplayMode *mode = [[modeClass() alloc] initWithWidth:width height:height refreshRate:hz];
            if (!mode) return failure(error, size, @"CGVirtualDisplayMode refused the mode");
            CGVirtualDisplaySettings *settings = [[settingsClass() alloc] init];
            settings.modes = @[mode];
            settings.hiDPI = hidpi ? 1 : 0;
            if (![virtualDisplay applySettings:settings])
                return failure(error, size, @"CGVirtualDisplay refused the settings");
            *display = virtualDisplay.displayID;
            return (__bridge_retained void *)virtualDisplay;
        } @catch (NSException *e) {
            return failure(error, size, [NSString stringWithFormat:@"CGVirtualDisplay: %@", e.reason]);
        }
    }
}

// The display goes away once nothing holds it.
void ah_vd_destroy(void *display) {
    @autoreleasepool {
        (void)CFBridgingRelease(display);
    }
}

// The current mode of `display`: its size in points and in pixels (`out`: width, height, pixel width, pixel height).
// Returns 0 if it can't be read. The process that created a virtual display can't read (or set) its mode, other
// processes can (see mac.rs).
int ah_vd_mode(uint32_t display, uint32_t out[4]) {
    CGDisplayModeRef mode = CGDisplayCopyDisplayMode(display);
    if (!mode) return 0;
    out[0] = (uint32_t)CGDisplayModeGetWidth(mode);
    out[1] = (uint32_t)CGDisplayModeGetHeight(mode);
    out[2] = (uint32_t)CGDisplayModeGetPixelWidth(mode);
    out[3] = (uint32_t)CGDisplayModeGetPixelHeight(mode);
    CGDisplayModeRelease(mode);
    return 1;
}

// How long a detour mode stays before switching to the wanted one (see ah_vd_set_mode; 50 ms was enough in testing)
static const useconds_t DETOUR_WAIT_US = 100000;

// Switch `display` to `mode`, remembered by macOS or for this login session only (`option`). Returns a CGError.
static CGError configure_mode(uint32_t display, CGDisplayModeRef mode, CGConfigureOption option) {
    CGDisplayConfigRef config;
    CGError error = CGBeginDisplayConfiguration(&config);
    if (error) return error;
    error = CGConfigureDisplayWithDisplayMode(config, display, mode, NULL);
    if (error) {
        CGCancelDisplayConfiguration(config);
        return error;
    }
    return CGCompleteDisplayConfiguration(config, option);
}

// `mode` is `width` x `height` points
static int has_points(CGDisplayModeRef mode, uint32_t width, uint32_t height) {
    return CGDisplayModeGetWidth(mode) == width && CGDisplayModeGetHeight(mode) == height;
}

// `mode` is `width` x `height` points and `pixel_width` x `pixel_height` pixels
static int is_mode(CGDisplayModeRef mode, uint32_t width, uint32_t height, uint32_t pixel_width,
                   uint32_t pixel_height) {
    return has_points(mode, width, height) && CGDisplayModeGetPixelWidth(mode) == pixel_width &&
           CGDisplayModeGetPixelHeight(mode) == pixel_height;
}

// Switch `display` to its mode of `width` x `height` points and `pixel_width` x `pixel_height` pixels (the low
// resolution twins of HiDPI modes included), remembered by macOS: the next time this display is in the same set of
// displays, macOS picks this mode again. Returns a CGError, kCGErrorIllegalArgument if there's no such mode.
//
// WindowServer may skip the switch. Issue #40 (macOS 15.6): the virtual display, taken out of the mirror set when the
// lid closed, was put in its mode 0 (800x520 1x; the mode remembered for leaving the mirror set was an invalid 1x1),
// while WindowServer still had the wanted mode down as the current one, so switching to it was "desired mode change
// 9 -> 9", "No change between prior and requested configuration". So if the display isn't in the wanted mode
// afterwards, it goes through another mode first (for this login session only), then to the wanted one: one of the
// same size in points (the 1x twin of a HiDPI mode or the other way round: windows stay where they are; this is what
// fixed it on that Mac), or else the mode it's actually in (nothing changes on the screen). Never a mode of another
// size: macOS would squeeze the windows into it and leave them like that.
int ah_vd_set_mode(uint32_t display, uint32_t width, uint32_t height, uint32_t pixel_width, uint32_t pixel_height) {
    @autoreleasepool {
        NSDictionary *options = @{(__bridge NSString *)kCGDisplayShowDuplicateLowResolutionModes : @YES};
        CFArrayRef modes = CGDisplayCopyAllDisplayModes(display, (__bridge CFDictionaryRef)options);
        if (!modes) return kCGErrorIllegalArgument;
        CGDisplayModeRef wanted = NULL, twin = NULL;
        for (CFIndex i = 0; i < CFArrayGetCount(modes); i++) {
            CGDisplayModeRef mode = (CGDisplayModeRef)CFArrayGetValueAtIndex(modes, i);
            if (!CGDisplayModeIsUsableForDesktopGUI(mode)) continue;
            if (is_mode(mode, width, height, pixel_width, pixel_height)) {
                if (!wanted) wanted = mode;
            } else if (!twin && has_points(mode, width, height)) {
                twin = mode;
            }
        }
        CGError error = kCGErrorIllegalArgument;
        if (wanted) {
            error = configure_mode(display, wanted, kCGConfigurePermanently);
            CGDisplayModeRef now = error ? NULL : CGDisplayCopyDisplayMode(display);
            if (now && !is_mode(now, width, height, pixel_width, pixel_height)) {
                error = configure_mode(display, twin ? twin : now, kCGConfigureForSession);
                if (!error) {
                    usleep(DETOUR_WAIT_US);
                    error = configure_mode(display, wanted, kCGConfigurePermanently);
                }
            }
            if (now) CGDisplayModeRelease(now);
        }
        CFRelease(modes);
        return error;
    }
}

// Put `display` into the mirror set of `of` (0: take it out of any mirror set), for this login session only.
// Returns a CGError.
int ah_vd_mirror(uint32_t display, uint32_t of) {
    CGDisplayConfigRef config;
    CGError error = CGBeginDisplayConfiguration(&config);
    if (error) return error;
    error = CGConfigureDisplayMirrorOfDisplay(config, display, of);
    if (error) {
        CGCancelDisplayConfiguration(config);
        return error;
    }
    return CGCompleteDisplayConfiguration(config, kCGConfigureForSession);
}
